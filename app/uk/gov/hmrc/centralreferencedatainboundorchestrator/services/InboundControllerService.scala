/*
 * Copyright 2024 HM Revenue & Customs
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
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.MessageStatus.Received
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.MessageWrapperRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

@Singleton
class InboundControllerService @Inject() (
  messageWrapperRepository: MessageWrapperRepository
)(using ec: ExecutionContext)
    extends Logging:

  def processMessage(xml: NodeSeq): Future[Boolean] =
    for
      uid   <- getUID(xml)
      dbRes <- messageWrapperRepository.insertMessageWrapper(uid, xml.toString, Received)
    yield dbRes

  private def getUID(xml: NodeSeq): Future[String] =
    (xml \\ "IncludedBinaryObject").text.trim match {
      case uid if uid != "" =>
        logger.info(s"Successfully extracted UID: $uid")
        Future.successful(uid)
      case _                =>
        logger.error("Failed to find UID in xml - potentially an error report")
        Future.failed(InvalidXMLContentError("Failed to find UID in xml - potentially an error report"))
    }
