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

package helpers

import scala.xml.Elem

object InboundSoapMessage {

  val valid_soap_message: Elem =
    <S:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope" xmlns:S="http://www.w3.org/2003/05/soap-envelope">
      <S:Header>
        <Action xmlns="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.Default.CSRD.ReferenceDataSubmissionResultReceiverCBS/ReceiveReferenceDataSubmissionResult</Action>
      </S:Header>
      <S:Body>
        <ReceiveReferenceDataSubmissionResult>
          <MessageHeader>
            <messageID>testMessageId123</messageID>
            <messageName>test message name</messageName>
            <sender>CS/RD2</sender>
            <recipient>DPS</recipient>
            <timeCreation>2023-10-03T16:00:00</timeCreation>
          </MessageHeader>
          <TaskIdentifier>TASKID12345</TaskIdentifier>
          <IncludedBinaryObject>c04a1612-705d-4373-8840-9d137b14b301</IncludedBinaryObject>
        </ReceiveReferenceDataSubmissionResult>
      </S:Body>
    </S:Envelope>

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
