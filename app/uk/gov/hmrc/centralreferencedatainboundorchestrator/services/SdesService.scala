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
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.MessageWrapperRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SdesService @Inject() (
                              messageWrapperRepository: MessageWrapperRepository
                            )(implicit executionContext: ExecutionContext) extends Logging:

  def processCallback(sdesCallback: SdesCallbackResponse): Future[String] = {
    sdesCallback.notification match {
      case "FileReceived" =>
        logger.info("AV Scan passed Successfully")
        messageWrapperRepository.findByUid(sdesCallback.correlationID) flatMap {
          case Some(value) => ???
          case None => Future.failed(NoMatchingUIDInMongoError(s"Failed to find a UID in Mongo matching: ${sdesCallback.correlationID}"))
        } 

      case "FileProcessingFailure" =>
        logger.info("AV Scan failed")
        messageWrapperRepository.updateStatus(sdesCallback.correlationID, MessageStatus.Failed) flatMap {
          case true => Future.successful(s"status updated to failed for uid: ${sdesCallback.correlationID}") 
          case false => Future.failed(MongoWriteError(s"failed to update message wrappers status to failed with uid: ${sdesCallback.correlationID}"))
        }
    }
  }
