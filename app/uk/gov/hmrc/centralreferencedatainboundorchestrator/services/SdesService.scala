/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.centralreferencedatainboundorchestrator.services

import play.api.Logging
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.connectors.EisConnector
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.MessageStatus.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.{EISWorkItemRepository, MessageWrapperRepository}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.XML.*

@Singleton
class SdesService @Inject() (
  messageWrapperRepository: MessageWrapperRepository,
  workItemRepo: EISWorkItemRepository,
  eisConnector: EisConnector,
  appConfig: AppConfig
)(using executionContext: ExecutionContext)
    extends Logging:

  def processCallback(sdesCallback: SdesCallbackResponse)(using hc: HeaderCarrier): Future[String] =
    sdesCallback.notification match {
      case "FileReceived" =>
        logger.info(s"AV Scan passed Successfully for uid: ${sdesCallback.correlationID}")
        updateMessageStatus(sdesCallback, Pass)

      case "FileProcessed" =>
        logger.info(s"File has now been delivered to the HMRC recipient for uid: ${sdesCallback.correlationID}")
        forwardMessage(sdesCallback)

      case "FileProcessingFailure" =>
        logger.info(s"AV Scan failed for uid: ${sdesCallback.correlationID}")
        updateMessageStatus(sdesCallback, Fail)

      case invalidNotification =>
        logger.warn(s"SDES notification not recognised: $invalidNotification")
        Future.failed(InvalidSDESNotificationError(s"SDES notification not recognised: $invalidNotification"))
    }

  def sendMessage(payload: String)(using hc: HeaderCarrier): Future[Boolean] =
    eisConnector.forwardMessage(loadString(payload))

  def updateStatus(messageSent: Boolean, correlationID: String): Future[String] =
    if messageSent then
      messageWrapperRepository.updateStatus(correlationID, Sent) flatMap {
        case true  =>
          Future.successful(s"Message with UID: $correlationID, successfully sent to EIS and status updated to sent.")
        case false =>
          Future.failed(MongoWriteError(s"failed to update message wrappers status to failed with uid: $correlationID"))
      }
    else
      logger.error("Message not sent")
      Future.failed(EisResponseError(s"Unable to send message to EIS after ${appConfig.maxRetryCount} attempts"))

  private def updateMessageStatus(sdesCallback: SdesCallbackResponse, status: MessageStatus) =
    messageWrapperRepository.updateStatus(sdesCallback.correlationID, status) flatMap {
      case true  => Future.successful(s"status updated to failed for uid: ${sdesCallback.correlationID}")
      case false =>
        Future.failed(
          MongoWriteError(s"failed to update message wrappers status to failed with uid: ${sdesCallback.correlationID}")
        )
    }

  private def forwardMessage(sdesCallback: SdesCallbackResponse) =
    messageWrapperRepository.findByUid(sdesCallback.correlationID) flatMap {
      case Some(messageWrapper) =>
        workItemRepo
          .set(EISRequest(messageWrapper.payload, sdesCallback.correlationID, messageWrapper.messageType))
          .map(_ => s"Message with UID: ${sdesCallback.correlationID}, successfully queued")
      case None                 =>
        logger.error(s"failed to retrieve message wrapper with uid: ${sdesCallback.correlationID}")
        Future.failed(
          NoMatchingUIDInMongoError(s"Failed to find a UID in Mongo matching: ${sdesCallback.correlationID}")
        )
    }
