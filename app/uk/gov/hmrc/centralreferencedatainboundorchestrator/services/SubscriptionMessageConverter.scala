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
    val CommonService: String     = "http://xmlns.ec.eu/BusinessObjects/CSRD2/CommonServiceType/V3"
    val MessageHeader: String     = "http://xmlns.ec.eu/BusinessObjects/CSRD2/MessageHeaderType/V2"
    val RDEntityEntryList: String = "http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntityEntryListType/V3"
    val RDEntity: String          = "http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntityType/V3"
    val RDEntry: String           = "http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntryType/V3"
    val RDStatus: String          = "http://xmlns.ec.eu/BusinessObjects/CSRD2/RDStatusType/V3"
    val RDValidityPeriod: String  = "http://xmlns.ec.eu/BusinessObjects/CSRD2/RDValidityPeriodType/V2"
    val LsdList: String           = "http://xmlns.ec.eu/BusinessObjects/CSRD2/LsdListType/V2"
  }

  private val CanonicalPrefixMap: Map[String, String] = Map(
    Namespaces.Root              -> "ns0",
    Namespaces.CommonService     -> "ns3",
    Namespaces.MessageHeader     -> "ns9",
    Namespaces.RDEntityEntryList -> "ns10",
    Namespaces.RDEntity          -> "ns12",
    Namespaces.RDEntry           -> "ns15",
    Namespaces.RDStatus          -> "ns16",
    Namespaces.RDValidityPeriod  -> "ns17",
    Namespaces.LsdList           -> "ns7"
  )

  private object Elements {
    val Body: String                            = "Body"
    val ReceiveReferenceDataReqMsg: String      = "ReceiveReferenceDataReqMsg"
    val ReceiveReferenceDataRequestType: String = "ReceiveReferenceDataRequestType"
    val MessageHeader: String                   = "MessageHeader"
    val ErrorReport: String                     = "ErrorReport"
    val RDEntityList: String                    = "RDEntityList"
  }

  private val XmlDeclaration: String = """<?xml version="1.0" encoding="UTF-8"?>"""

  private def remapToCanonical(node: Node): Node = node match {
    case elem: Elem =>
      val newPrefix = Option(elem.namespace) match {
        case Some(uri) => CanonicalPrefixMap.getOrElse(uri, elem.prefix)
        case None      => elem.prefix
      }
      elem.copy(
        prefix = newPrefix,
        scope = TopScope,
        child = elem.child.map(remapToCanonical)
      )
    case other      => other
  }

  private def normalizeTextNodes(node: Node): Seq[Node] = node match {
    case elem: Elem =>
      Seq(elem.copy(scope = TopScope, child = elem.child.flatMap(normalizeTextNodes)))
    case Text(t)    =>
      val s = t.trim
      if s.isEmpty then Seq.empty else Seq(Text(s))
    case other      => Seq(other)
  }

  private def convertSoapMessage(soapXml: Elem): Elem =
    try {
      val body           = (soapXml \\ Elements.Body).head
      val receiveDataMsg = (body \\ Elements.ReceiveReferenceDataReqMsg).head
      val requestType    =
        (receiveDataMsg \\ Elements.ReceiveReferenceDataRequestType).headOption.getOrElse(receiveDataMsg)

      val msgHeaderNode = (requestType \\ Elements.MessageHeader).head
      val errorReport   = (requestType \\ Elements.ErrorReport).headOption

      errorReport match {
        case Some(error) =>
          buildErrorMessage(msgHeaderNode, error.text)
        case None        =>
          buildDataMessage(msgHeaderNode, (requestType \\ Elements.RDEntityList).head)
      }
    } catch {
      case e: Exception =>
        throw new RuntimeException(s"Failed to convert SOAP message: ${e.getMessage}", e)
    }

  private def buildDataMessage(msgHeaderNode: Node, rdEntityList: Node): Elem =
    <ns0:HMRCReceiveReferenceDataReqMsg
      xmlns:ns7={Namespaces.LsdList}
      xmlns:ns17={Namespaces.RDValidityPeriod}
      xmlns:ns16={Namespaces.RDStatus}
      xmlns:ns15={Namespaces.RDEntry}
      xmlns:ns12={Namespaces.RDEntity}
      xmlns:ns10={Namespaces.RDEntityEntryList}
      xmlns:ns9={Namespaces.MessageHeader}
      xmlns:ns3={Namespaces.CommonService}
      xmlns:ns0={Namespaces.Root}>
      {remapToCanonical(msgHeaderNode)}
      {remapToCanonical(rdEntityList)}
    </ns0:HMRCReceiveReferenceDataReqMsg>

  private def buildErrorMessage(msgHeaderNode: Node, errorText: String): Elem =
    <ns0:HMRCReceiveReferenceDataReqMsg
      xmlns:ns0={Namespaces.Root}
      xmlns:ns9={Namespaces.MessageHeader}>
      {remapToCanonical(msgHeaderNode)}
      <ns0:ErrorReport>{errorText}</ns0:ErrorReport>
    </ns0:HMRCReceiveReferenceDataReqMsg>

  def convertSoapString(soapXmlString: String): String = {
    val root       = convertSoapMessage(XML.loadString(soapXmlString))
    val xmlPayload = root.copy(child = root.child.flatMap(normalizeTextNodes))
    s"$XmlDeclaration\n$xmlPayload"
  }
}
