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
import play.api.http.Status.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.connectors.EisConnector
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.MessageWrapperRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import scala.xml.XML.*

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class SdesService @Inject() (
                              messageWrapperRepository: MessageWrapperRepository,
                              eisConnector: EisConnector
                            )(using executionContext: ExecutionContext) extends Logging:

  def processCallback(sdesCallback: SdesCallbackResponse)(using hc: HeaderCarrier): Future[String] = {
    sdesCallback.notification match {
      case "FileReceived" =>
        forwardMessage(sdesCallback)

      case "FileProcessingFailure" =>
        updateMessageToFailed(sdesCallback)

      case invalidNotification =>
        Future.failed(InvalidSDESNotificationError(s"SDES notification not recognised: $invalidNotification"))
    }
  }

  private def updateMessageToFailed(sdesCallback: SdesCallbackResponse) = {
    logger.info("AV Scan failed")
    messageWrapperRepository.updateStatus(sdesCallback.correlationID, MessageStatus.Failed) flatMap {
      case true => Future.successful(s"status updated to failed for uid: ${sdesCallback.correlationID}")
      case false => Future.failed(MongoWriteError(s"failed to update message wrappers status to failed with uid: ${sdesCallback.correlationID}"))
    }
  }

  private def forwardMessage(sdesCallback: SdesCallbackResponse)(using hc: HeaderCarrier) = {
    logger.info("AV Scan passed Successfully")
    messageWrapperRepository.findByUid(sdesCallback.correlationID) flatMap {
      case Some(messageWrapper) => eisConnector.forwardMessage(loadString(messageWrapper.payload)).flatMap(response => resultFromStatus(response, sdesCallback))
      case None => 
        logger.error(s"failed to retrieve message wrapper with uid: ${sdesCallback.correlationID}")
        Future.failed(NoMatchingUIDInMongoError(s"Failed to find a UID in Mongo matching: ${sdesCallback.correlationID}"))
    }
  }

  private def resultFromStatus(response: HttpResponse, sdesCallback: SdesCallbackResponse): Future[String] = {
    response.status match {
      case ACCEPTED =>
        messageWrapperRepository.updateStatus(sdesCallback.correlationID, MessageStatus.Sent) flatMap {
          case true => Future.successful(s"Message with UID: ${sdesCallback.correlationID}, successfully sent to EIS with ${response.status} & status updated to sent")
          case false => Future.failed(MongoWriteError(s"failed to update message wrappers status to sent with uid: ${sdesCallback.correlationID}"))
        }
      case status =>
        Future.failed(
        EisResponseError(s"Non 202 response received from EIS: HTTP $status with body: ${response.body}")
      )
    }
  }
