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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.helpers

import scala.xml.Elem

object InboundSoapMessage {

  def valid_soap_message_with_id(uid: String): Elem =
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
                   xmlns:v4="http://xmlns.ec.eu/CallbackService/CSRD2/IReferenceDataExportReceiverCBS/V4"
                   xmlns:v41="http://xmlns.ec.eu/BusinessObjects/CSRD2/ReferenceDataExportReceiverCBSServiceType/V4"
                   xmlns:v2="http://xmlns.ec.eu/BusinessObjects/CSRD2/MessageHeaderType/V2">
      <soap:Header>
        <Action xmlns="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.Default.CSRD.ReferenceDataExportReceiverCBS/ReceiveReferenceData</Action>
        <MessageID xmlns="http://www.w3.org/2005/08/addressing">urn:uuid:fcb0896f-33d1-4542-8f64-1dce8101ca09</MessageID>
      </soap:Header>
      <soap:Body>
        <v4:ReceiveReferenceDataReqMsg>
          <v41:MessageHeader>
            <v2:messageID>testMessageId123</v2:messageID>
            <v2:messageName>test message name</v2:messageName>
            <v2:sender>CS/RD2</v2:sender>
            <v2:recipient>DPS</v2:recipient>
            <v2:timeCreation>2023-10-03T16:00:00</v2:timeCreation>
          </v41:MessageHeader>
          <v41:TaskIdentifier>TASKID12345</v41:TaskIdentifier>
          <v41:ReceiveReferenceDataRequestResult>{uid}</v41:ReceiveReferenceDataRequestResult>
        </v4:ReceiveReferenceDataReqMsg>
      </soap:Body>
    </soap:Envelope>

  val valid_soap_message: Elem = valid_soap_message_with_id("c04a1612-705d-4373-8840-9d137b14b301")

  val valid_soap_is_alive_message: Elem =
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
                   xmlns:v1="http://xmlns.ec.eu/BusinessMessages/TATAFng/Monitoring/V1">
      <soap:Header>
        <Action xmlns="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.Default.CSRD.ReferenceDataExportReceiverCBS/IsAlive</Action>
        <MessageID xmlns="http://www.w3.org/2005/08/addressing">urn:uuid:fcb0896f-33d1-4542-8f64-1dce8101ca09</MessageID>
      </soap:Header>
      <soap:Body>
        <v1:isAliveReqMsg/>
      </soap:Body>
    </soap:Envelope>

  val valid_soap_error_report_message: Elem =
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
                     xmlns:v4="http://xmlns.ec.eu/CallbackService/CSRD2/IReferenceDataExportReceiverCBS/V4"
                     xmlns:v41="http://xmlns.ec.eu/BusinessObjects/CSRD2/ReferenceDataExportReceiverCBSServiceType/V4"
                     xmlns:v2="http://xmlns.ec.eu/BusinessObjects/CSRD2/MessageHeaderType/V2">
        <soap:Header>
          <Action xmlns="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.Default.CSRD.ReferenceDataExportReceiverCBS/ReceiveReferenceData</Action>
          <MessageID xmlns="http://www.w3.org/2005/08/addressing">urn:uuid:fcb0896f-33d1-4542-8f64-1dce8101ca09</MessageID>
        </soap:Header>
        <soap:Body>
          <v4:ReceiveReferenceDataReqMsg>
            <v41:MessageHeader>
              <v2:messageID>testMessageId123</v2:messageID>
              <v2:messageName>test message name</v2:messageName>
              <v2:sender>CS/RD2</v2:sender>
              <v2:recipient>DPS</v2:recipient>
              <v2:timeCreation>2023-10-03T16:00:00</v2:timeCreation>
            </v41:MessageHeader>
            <v41:TaskIdentifier>TASKID12345</v41:TaskIdentifier>
            <v41:ErrorReport>c04a1612-705d-4373-8840-9d137b14b301</v41:ErrorReport>
          </v4:ReceiveReferenceDataReqMsg>
        </soap:Body>
      </soap:Envelope>

  val invalid_soap_message: Elem = <MainMessage>
    <Body>
      <TaskIdentifier>780912</TaskIdentifier>
      <AttributeName>ReferenceData</AttributeName>
      <MessageType>gZip</MessageType>
      <IncludedBinaryObject>c04a1612-705d-4373-8840-9d137b14b30a</IncludedBinaryObject>
      <MessageSender>CS/RD2</MessageSender>
    </Body>
  </MainMessage>
}
