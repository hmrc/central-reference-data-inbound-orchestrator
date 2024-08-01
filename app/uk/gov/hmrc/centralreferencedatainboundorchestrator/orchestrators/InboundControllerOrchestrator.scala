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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.orchestrators

import play.api.Logging

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.MessageWrapperRepository
import uk.gov.hmrc.centralreferencedatainboundorchestrator.utils.EncodingUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

@Singleton
class InboundControllerOrchestrator @Inject() (
            messageWrapperRepository: MessageWrapperRepository
                                              )(using ec: ExecutionContext) extends Logging:

  def processMessage(xml: NodeSeq): Future[Boolean] =
    for
      uid <- getUID(xml)
      dbRes <- messageWrapperRepository.insertMessageWrapper(uid, xml.toString, "RECEIVED")
    yield dbRes

  private def getUID(xml: NodeSeq): Future[String] =
    Try((xml \\ "includedBinaryObject").text.trim) match {
      case Success(uid) =>
        logger.info("Successfully extracted UID")
        Future.successful(EncodingUtils.decodeBase64(uid))
      case Failure(ex) =>
        logger.error("failed to find UID")
        Future.failed(ex)
  }
