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

object SubscriptionChangeResponse {
  val acknowledgement = (messageId: String) =>
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
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
