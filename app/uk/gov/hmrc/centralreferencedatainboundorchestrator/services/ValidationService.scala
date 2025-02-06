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

import org.w3c.dom.Document
import play.api.Logging

import java.io.{ByteArrayInputStream, StringReader}
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.Source
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.{Schema, SchemaFactory}
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, Node, NodeSeq}
import scala.xml.Utility.trim

class ValidationService extends Logging:
  private lazy val soapSchema: Schema = {
    val factory             = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    val soapXsd             = getClass.getResourceAsStream("/schemas/soap-envelope.xsd")
    val streamSourceSoapXsd = new StreamSource(soapXsd)
    val xmlXsd              = getClass.getResourceAsStream("/schemas/xml.xsd")
    val streamSourceXmlXsd  = new StreamSource(xmlXsd)
    val schema              = factory.newSchema(Array[Source](streamSourceXmlXsd, streamSourceSoapXsd))
    streamSourceSoapXsd.getInputStream.close()
    streamSourceXmlXsd.getInputStream.close()
    schema
  }

  private lazy val bodySchema: Schema = {
    val factory             = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    val bodyXsd             = getClass.getResourceAsStream("/schemas/receive-reference-data-submission-result.xsd")
    val streamSourceBodyXsd = new StreamSource(bodyXsd)
    val schema              = factory.newSchema(streamSourceBodyXsd)
    streamSourceBodyXsd.getInputStream.close()
    schema
  }

  private[services] def validateSoapMessage(soapMessage: NodeSeq): Boolean = {
    val bis            = new ByteArrayInputStream(soapMessage.toString.getBytes)
    val builderFactory = DocumentBuilderFactory.newInstance()
    builderFactory.setNamespaceAware(true)
    val builder        = builderFactory.newDocumentBuilder()
    val xml: Document  = builder.parse(bis)
    val validator      = soapSchema.newValidator()
    Try {
      validator.validate(new DOMSource(xml))
      true
    } match {
      case Success(valid) => true
      case Failure(ex)    => false
    }
  }

  private[services] def getSoapBody(soapMessage: NodeSeq): Option[Node] =
    (soapMessage \\ "Body").headOption
      .fold[Option[Node]](None)(body => body.child.collectFirst { case e: Elem => e })

  private[services] def validateMessageWrapper(messageWrapper: NodeSeq): Boolean =
    Try {
      val validator = bodySchema.newValidator()
      validator.validate(new StreamSource(new StringReader(messageWrapper.toString)))
      true
    } match {
      case Success(_)  => true
      case Failure(ex) =>
        logger.error(
          s"Failed to validate schema of message - potentially an error report with body: $messageWrapper",
          ex
        )
        false
    }

  private[services] def extractInnerMessage(body: NodeSeq): NodeSeq = {
    val taskId             = (body \\ "ReceiveReferenceDataSubmissionResult" \ "TaskIdentifier").text
    val correlationId      = (body \\ "ReceiveReferenceDataSubmissionResult" \ "IncludedBinaryObject").text
    val innerMessage: Elem = <MainMessage>
      <Body>
        <TaskIdentifier>{taskId}</TaskIdentifier>
        <AttributeName>ReferenceData</AttributeName>
        <MessageType>gZip</MessageType>
        <IncludedBinaryObject>{correlationId}</IncludedBinaryObject>
        <MessageSender>CS/RD2</MessageSender>
      </Body>
    </MainMessage>
    trim(innerMessage)
  }

  def validateFullSoapMessage(soapMessage: NodeSeq): Option[NodeSeq] =
    if validateSoapMessage(soapMessage) then
      for {
        body        <- getSoapBody(soapMessage)
        if validateMessageWrapper(body)
        innerMessage = extractInnerMessage(body)
      } yield innerMessage
    else None
