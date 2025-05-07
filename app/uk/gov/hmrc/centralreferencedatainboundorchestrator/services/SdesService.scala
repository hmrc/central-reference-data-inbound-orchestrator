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

  def processCallback(sdesCallback: SdesCallbackResponse)(using hc: HeaderCarrier): Future[Unit] =
    sdesCallback.notification match {
      case "FileReceived" =>
        logger.info(s"AV Scan passed Successfully for uid: ${sdesCallback.correlationID}")
        updateMessageStatus(sdesCallback, expectedStatus = Received, newStatus = Pass)

      case "FileProcessingFailure" =>
        logger.info(s"AV Scan failed for uid: ${sdesCallback.correlationID}")
        updateMessageStatus(sdesCallback, expectedStatus = Received, newStatus = Fail)

      case "FileProcessed" =>
        logger.info(s"File has now been delivered to the HMRC recipient for uid: ${sdesCallback.correlationID}")
        forwardMessage(sdesCallback)

      case invalidNotification =>
        logger.warn(s"SDES notification not recognised: $invalidNotification")
        Future.failed(InvalidSDESNotificationError(s"SDES notification not recognised: $invalidNotification"))
    }

  def sendMessage(payload: String)(using hc: HeaderCarrier): Future[Boolean] =
    eisConnector.forwardMessage(loadString(payload))

  def updateStatus(messageSent: Boolean, correlationID: String): Future[Unit] =
    if messageSent then
      messageWrapperRepository.updateStatus(correlationID, expectedStatus = Submitted, newStatus = Sent)
    else
      logger.error(s"Unable to send message to EIS after ${appConfig.maxRetryCount} attempts")
      Future.failed(EisResponseError(s"Unable to send message to EIS after ${appConfig.maxRetryCount} attempts"))

  private def updateMessageStatus(
    sdesCallback: SdesCallbackResponse,
    expectedStatus: MessageStatus,
    newStatus: MessageStatus
  ) =
    messageWrapperRepository.findByUidAndUpdateStatus(sdesCallback.correlationID, expectedStatus, newStatus).flatMap {
      case Some(messageWrapper) => Future.unit
      case None                 =>
        logger.error(
          s"Failed to find a message wrapper with ID ${sdesCallback.correlationID} and status $expectedStatus"
        )
        Future.failed(
          NoMatchingUIDInMongoError(
            s"Failed to find a message wrapper with ID ${sdesCallback.correlationID} and status $expectedStatus"
          )
        )
    }

  private def forwardMessage(sdesCallback: SdesCallbackResponse) =
    messageWrapperRepository
      .findByUidAndUpdateStatus(
        sdesCallback.correlationID,
        expectedStatus = Pass,
        newStatus = Submitted
      )
      .flatMap {
        case Some(messageWrapper) =>
          workItemRepo.set(EISRequest(messageWrapper.payload, sdesCallback.correlationID)).map(_ => ())
        case None                 =>
          logger.error(s"Failed to find a message wrapper with ID ${sdesCallback.correlationID} and status $Pass")
          Future.failed(
            NoMatchingUIDInMongoError(
              s"Failed to find a message wrapper with ID ${sdesCallback.correlationID} and status $Pass"
            )
          )
      }
