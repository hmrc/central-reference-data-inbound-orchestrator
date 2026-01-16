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
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.SoapAction

import javax.inject.Inject
import scala.util.Try
import scala.xml.Utility.trim
import scala.xml.factory.XMLLoader
import scala.xml.{Elem, Node, NodeSeq, XML}

class ValidationService @Inject() (val appConfig: AppConfig, val loader: ValidatingXmlLoader) extends Logging:

  def validateAndExtractAction(soapMessage: String): Option[(SoapAction, Node)] =
    for
      preliminaryParse <- scala.util.Try(XML.loadString(soapMessage)).toOption
      soapAction       <- extractSoapAction(preliminaryParse)
      validatedMessage <- validateSoapMessage(soapMessage, soapAction)
    yield (soapAction, validatedMessage)

  private def validateSoapMessage(loader: XMLLoader[Elem], soapMessage: String, action: SoapAction): Option[Elem] = {
    loader match {
      case validatingLoader: ValidatingXmlLoader => validatingLoader.setSchemaForAction(action)
      case _                                     =>
    }

    val loadMessage = Try(loader.loadString(soapMessage))

    loadMessage.failed.foreach { ex =>
      logger.error(
        s"Failed to validate schema of message for action $action: $soapMessage",
        ex
      )
    }

    loadMessage.toOption
  }

  def extractInnerMessage(soapMessage: NodeSeq): Option[Node] =
    for
      requestMessage <- (soapMessage \\ "Body" \ "ReceiveReferenceDataReqMsg").headOption
      taskId         <- (requestMessage \ "TaskIdentifier").headOption
      correlationId  <- (requestMessage \ "ReceiveReferenceDataRequestResult").headOption.orElse(
                          (requestMessage \ "ErrorReport").headOption
                        )
    yield trim(
      <MainMessage>
        <Body>
          <TaskIdentifier>{taskId.text}</TaskIdentifier>
          <AttributeName>ReferenceData</AttributeName>
          <MessageType>gZip</MessageType>
          <IncludedBinaryObject>{correlationId.text}</IncludedBinaryObject>
          <MessageSender>CS/RD2</MessageSender>
        </Body>
      </MainMessage>
    )

  def extractSoapAction(soapMessage: NodeSeq): Option[SoapAction] =
    for
      actionNode <- (soapMessage \\ "Header" \ "Action").headOption
      action     <- SoapAction.fromString(actionNode.text.trim)
    yield action

  private def validateSoapMessage(soapMessage: String, action: SoapAction): Option[Node] =
    validateSoapMessage(if appConfig.xsdValidation then loader else XML, soapMessage, action)
