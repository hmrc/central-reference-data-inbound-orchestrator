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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.models
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig

import scala.xml.Elem

object SubscriptionChangeResponse {
  def acknowledgement(messageId: String, responseMessageId: String, appConfig: AppConfig) =
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
        <soap:Header xmlns:wsa="http://www.w3.org/2005/08/addressing">
            <wsa:From>
                <wsa:Address>{appConfig.subscriptionResponsePartner}</wsa:Address>
            </wsa:From>
            <wsa:To>http://www.w3.org/2005/08/addressing/anonymous</wsa:To>
            <wsa:Action xmlns="http://www.w3.org/2005/08/addressing">{
      SoapAction.ReferenceDataSubscriptionAction
    }</wsa:Action>
            <wsa:RelatesTo>{messageId}</wsa:RelatesTo>
            <wsa:MessageID xmlns="http://www.w3.org/2005/08/addressing">{responseMessageId}</wsa:MessageID>
            <m:MessageHeader xmlns:m="http://ccn2.ec.eu/CCN2.Service.Platform.Common.Schema">
                <m:Version>1.0</m:Version>
                <m:MessageType>Orchestrator subscription response</m:MessageType>
            </m:MessageHeader>
        </soap:Header>
       <soap:Body>
            <ns15:ReceiveReferenceDataRespMsg xmlns:ns0="http://xmlns.ec.eu/BusinessObjects/CSRD2/ReferenceDataSubscriptionReceiverCBSServiceType/V4"
            xmlns:ns15="http://xmlns.ec.eu/CallbackService/CSRD2/IReferenceDataSubscriptionReceiverCBS/V4"
            xmlns:ns3="http://xmlns.ec.eu/BusinessObjects/CSRD2/CommonServiceType/V3"
            xmlns:ns5="http://xmlns.ec.eu/BusinessObjects/CSRD2/MessageHeaderType/V2">
                <ns3:MessageHeader>
                    <ns5:AddressingInformation>
                        <ns5:messageID>{messageId}</ns5:messageID>
                    </ns5:AddressingInformation>
                </ns3:MessageHeader>
                <ns3:acknowledgement>OK</ns3:acknowledgement>
            </ns15:ReceiveReferenceDataRespMsg>
        </soap:Body>
    </soap:Envelope>
}
