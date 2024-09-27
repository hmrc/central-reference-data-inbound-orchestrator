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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.xml.{Elem, Node}
import scala.xml.Utility.trim

class ValidationServiceSpec extends AnyWordSpec, Matchers, ScalaFutures:
    private val validationService = ValidationService()

    private val valid_soap_message: Elem =
        <S:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope" xmlns:S="http://www.w3.org/2003/05/soap-envelope">
            <S:Header>
                <Action xmlns="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.Default.CSRD.ReferenceDataSubmissionResultReceiverCBS/ReceiveReferenceDataSubmissionResult</Action></S:Header>
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

    private val invalid_soap_message: Elem =
        <S:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope" xmlns:S="http://www.w3.org/2003/05/soap-envelope">
            <S:Header>
                <Action xmlns="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.Default.CSRD.ReferenceDataSubmissionResultReceiverCBS/ReceiveReferenceDataSubmissionResult</Action>
            </S:Header>
            <ExtraNode>
                <Should>
                    <Not>
                        <Be>
                            <Present/>
                        </Be>
                    </Not>
                </Should>
            </ExtraNode>
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

    private val valid_soap_message_without_body: Elem =
        <S:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope" xmlns:S="http://www.w3.org/2003/05/soap-envelope">
            <S:Header>
                <Action xmlns="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.Default.CSRD.ReferenceDataSubmissionResultReceiverCBS/ReceiveReferenceDataSubmissionResult</Action>
            </S:Header>
        </S:Envelope>

    private val valid_soap_message_with_invalid_body: Elem =
        <S:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope" xmlns:S="http://www.w3.org/2003/05/soap-envelope">
            <S:Header>
                <Action xmlns="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.Default.CSRD.ReferenceDataSubmissionResultReceiverCBS/ReceiveReferenceDataSubmissionResult</Action>
            </S:Header>
            <S:Body>
                <ExtraNode>
                    <Should>
                        <Not>
                            <Be>
                                <Present/>
                            </Be>
                        </Not>
                    </Should>
                </ExtraNode>
            </S:Body>
        </S:Envelope>

    private val valid_message_body: Node =
        <ReceiveReferenceDataSubmissionResult xmlns:env="http://www.w3.org/2003/05/soap-envelope" xmlns:S="http://www.w3.org/2003/05/soap-envelope">
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

    private val invalid_message_body: Elem =
        <ExtraNode>
            <Should>
                <Not>
                    <Be>
                        <Present/>
                    </Be>
                </Not>
            </Should>
        </ExtraNode>

    private val valid_inner_message: Elem =
        <MainMessage>
            <Body>
                <TaskIdentifier>TASKID12345</TaskIdentifier>
                <AttributeName>ReferenceData</AttributeName>
                <MessageType>gZip</MessageType>
                <IncludedBinaryObject>c04a1612-705d-4373-8840-9d137b14b301</IncludedBinaryObject>
                <MessageSender>CS/RD2</MessageSender>
            </Body>
        </MainMessage>

    "validation service" should {
        "validate a well formed soap message" in {
            validationService.validateSoapMessage(valid_soap_message) shouldBe true
        }

        "not validate a badly formed soap message" in {
            validationService.validateSoapMessage(invalid_soap_message) shouldBe false
        }

        "extract the body from a soap message" in {
            val actual = validationService.getSoapBody(valid_soap_message)
            trim(actual.get) shouldBe trim(valid_message_body)
        }

        "attempt to extract the body form a soap message that does not include one" in {
            val actual = validationService.getSoapBody(valid_soap_message_without_body)
            actual shouldBe None
        }

        "validate message body" in {
            validationService.validateMessageWrapper(valid_message_body) shouldBe true
        }

        "not validate a bad message body" in {
            validationService.validateMessageWrapper(invalid_message_body) shouldBe false
        }
        
        "extract inner message from the message body" in {
            val actual = validationService.extractInnerMessage(valid_message_body)
            trim(actual.head) shouldBe trim(valid_inner_message)
        }

        "completely validate a good soap message" in {
            val actual = validationService.validateFullSoapMessage(valid_soap_message).get
            actual.head shouldBe trim(valid_inner_message)
        }

        "completely validate a bad soap message" in {
            validationService.validateFullSoapMessage(invalid_soap_message) shouldBe None
        }

        "completely validate a good soap message with a bad body" in {
            validationService.validateFullSoapMessage(valid_soap_message_with_invalid_body) shouldBe None
        }
    }
