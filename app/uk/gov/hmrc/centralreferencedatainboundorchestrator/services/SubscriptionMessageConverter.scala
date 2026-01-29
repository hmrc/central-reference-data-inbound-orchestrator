/*
 * Copyright 2026 HM Revenue & Customs
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

import scala.xml.*

object SubscriptionMessageConverter {

  private object Namespaces {
    val Root: String              = "http://xmlns.ec.eu/BusinessObjects/CSRD2/ReferenceDataSubscriptionReceiverCBSServiceType/V4"
    val MessageHeader: String     = "http://xmlns.ec.eu/BusinessObjects/CSRD2/MessageHeaderType/V2"
    val RDEntityEntryList: String = "http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntityEntryListType/V3"
    val RDEntity: String          = "http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntityType/V3"
    val RDEntry: String           = "http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntryType/V3"
    val RDStatus: String          = "http://xmlns.ec.eu/BusinessObjects/CSRD2/RDStatusType/V3"
    val LsdList: String           = "http://xmlns.ec.eu/BusinessObjects/CSRD2/LsdListType/V2"
  }

  private object Elements {
    val Body: String                            = "Body"
    val ReceiveReferenceDataReqMsg: String      = "ReceiveReferenceDataReqMsg"
    val ReceiveReferenceDataRequestType: String = "ReceiveReferenceDataRequestType"
    val MessageHeader: String                   = "MessageHeader"
    val MessageID: String                       = "MessageID"
    val MessageTimestamp: String                = "MessageTimestamp"
    val MessageDateTime: String                 = "MessageDateTime"
    val SenderID: String                        = "SenderID"
    val ReceiverID: String                      = "ReceiverID"
    val ErrorReport: String                     = "ErrorReport"
    val RDEntityList: String                    = "RDEntityList"
    val RDEntity: String                        = "RDEntity"
    val RDEntry: String                         = "RDEntry"
    val Name: String                            = "name"
    val NameShort: String                       = "n"
    val State: String                           = "state"
    val Status: String                          = "status"
    val ActiveFrom: String                      = "activeFrom"
    val ValidFrom: String                       = "validFrom"
    val LsdList: String                         = "LsdList"
    val Lsd: String                             = "Lsd"
    val Description: String                     = "description"
    val DataItem: String                        = "dataItem"
    val Code: String                            = "code"
    val AddressingInformation: String           = "AddressingInformation"
    val RDEntryStatus: String                   = "RDEntryStatus"
  }

  private object Attributes {
    val Name: String = "name"
    val Lang: String = "lang"
  }

  private object Defaults {
    val State: String       = "valid"
    val Language: String    = "en"
    val EmptyString: String = ""
  }

  private val XmlDeclaration: String = """<?xml version="1.0" encoding="UTF-8"?>"""

  private val PrettyPrinterWidth: Int  = 80
  private val PrettyPrinterIndent: Int = 2

  case class MessageHeader(
    messageID: String,
    messageDateTime: String,
    senderID: String,
    receiverID: String
  )

  case class RDEntity(
    name: String,
    state: String,
    activeFrom: String,
    description: String,
    descriptionLang: String = Defaults.Language,
    code: Option[String] = None
  )

  private def convertSoapMessage(soapXml: Elem): Elem =
    try {
      val body           = (soapXml \\ Elements.Body).head
      val receiveDataMsg = (body \\ Elements.ReceiveReferenceDataReqMsg).head
      val requestType    = (receiveDataMsg \\ Elements.ReceiveReferenceDataRequestType).head

      val header = extractMessageHeader(requestType)

      val errorReport = (requestType \\ Elements.ErrorReport).headOption

      errorReport match {
        case Some(error) =>
          buildErrorMessage(header, error.text)
        case None        =>
          val entities = extractEntities(requestType)
          buildDataMessage(header, entities)
      }
    } catch {
      case e: Exception =>
        throw new RuntimeException(s"Failed to convert SOAP message: ${e.getMessage}", e)
    }

  private def extractMessageHeader(requestType: Node): MessageHeader = {
    val msgHeader = (requestType \\ Elements.MessageHeader).head

    val timestamp = (msgHeader \\ Elements.MessageTimestamp).headOption
      .orElse((msgHeader \\ Elements.MessageDateTime).headOption)
      .map(_.text)
      .getOrElse(Defaults.EmptyString)

    MessageHeader(
      messageID = (msgHeader \\ Elements.MessageID).text,
      messageDateTime = timestamp,
      senderID = (msgHeader \\ Elements.SenderID).text,
      receiverID = (msgHeader \\ Elements.ReceiverID).text
    )
  }

  private def extractEntities(requestType: Node): Seq[RDEntity] = {
    val rdEntityList = (requestType \\ Elements.RDEntityList).head
    val entities     = rdEntityList \\ Elements.RDEntity

    entities.map { entity =>
      val name = (entity \@ Attributes.Name) match {
        case Defaults.EmptyString =>
          (entity \ Elements.NameShort).headOption
            .orElse((entity \ Elements.Name).headOption)
            .map(_.text)
            .getOrElse(Defaults.EmptyString)
        case n                    => n
      }

      val rdEntry = (entity \\ Elements.RDEntry).headOption

      rdEntry match {
        case Some(entry) =>
          val state = (entry \\ Elements.State).headOption
            .orElse((entry \\ Elements.Status).headOption)
            .map(_.text)
            .getOrElse(Defaults.State)

          val activeFrom = (entry \\ Elements.ActiveFrom).headOption
            .orElse((entry \\ Elements.ValidFrom).headOption)
            .map(_.text)
            .getOrElse(Defaults.EmptyString)

          val (description, lang) = (entry \\ Elements.LsdList \\ Elements.Description).headOption
            .orElse((entry \\ Elements.Lsd \\ Elements.Description).headOption) match {
            case Some(desc) =>
              val descLang = desc \@ Attributes.Lang match {
                case Defaults.EmptyString => Defaults.Language
                case l                    => l
              }
              (desc.text, descLang)
            case None       =>
              (name, Defaults.Language)
          }

          val dataItems = (entry \\ Elements.DataItem).map { item =>
            val itemName  = item \@ Attributes.Name
            val itemValue = item.text
            itemName -> itemValue
          }.toMap

          val code = dataItems.get(Elements.Code)

          RDEntity(
            name = name,
            state = state,
            activeFrom = activeFrom,
            description = description,
            descriptionLang = lang,
            code = code
          )

        case None =>
          RDEntity(
            name = name,
            state = Defaults.State,
            activeFrom = Defaults.EmptyString,
            description = name,
            code = None
          )
      }
    }
  }

  private def buildDataMessage(header: MessageHeader, entities: Seq[RDEntity]): Elem = {
    <HMRCReceiveReferenceDataReqMsg
    xmlns={Namespaces.Root}
    xmlns:mh={Namespaces.MessageHeader}
    xmlns:rdeelt={Namespaces.RDEntityEntryList}
    xmlns:rde={Namespaces.RDEntity}
    xmlns:rdet={Namespaces.RDEntry}
    xmlns:rdst={Namespaces.RDStatus}
    xmlns:lsdlt={Namespaces.LsdList}>
      <MessageHeader>
        <mh:AddressingInformation>
          <mh:messageID>{header.messageID}</mh:messageID>
        </mh:AddressingInformation>
      </MessageHeader>
      <RDEntityList>
        {entities.map(buildEntityElement)}
      </RDEntityList>
    </HMRCReceiveReferenceDataReqMsg>
  }

  private def buildErrorMessage(header: MessageHeader, errorText: String): Elem = {
    <HMRCReceiveReferenceDataReqMsg
    xmlns={Namespaces.Root}
    xmlns:mh={Namespaces.MessageHeader}>
      <MessageHeader>
        <mh:AddressingInformation>
          <mh:messageID>{header.messageID}</mh:messageID>
        </mh:AddressingInformation>
      </MessageHeader>
      <ErrorReport>{errorText}</ErrorReport>
    </HMRCReceiveReferenceDataReqMsg>
  }

  private def buildEntityElement(entity: RDEntity): Elem = {
    <rdeelt:RDEntity name={entity.name}>
      <rde:RDEntry>
        <rdet:RDEntryStatus>
          <rdst:state>{entity.state}</rdst:state>
          <rdst:activeFrom>{entity.activeFrom}</rdst:activeFrom>
        </rdet:RDEntryStatus>
        <rdet:LsdList>
          <lsdlt:description lang={entity.descriptionLang}>{entity.description}</lsdlt:description>
        </rdet:LsdList>
      </rde:RDEntry>
    </rdeelt:RDEntity>
  }

  def convertSoapString(soapXmlString: String): String = {
    val xmlPayload    = convertSoapMessage(XML.loadString(soapXmlString))
    val prettyPrinter = new PrettyPrinter(PrettyPrinterWidth, PrettyPrinterIndent)
    s"$XmlDeclaration\n${prettyPrinter.format(xmlPayload)}"
  }
}
