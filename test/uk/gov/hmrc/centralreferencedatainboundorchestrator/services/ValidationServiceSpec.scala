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

import org.mockito.Mockito.{reset, when}
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.SoapAction

import scala.xml.{Elem, Node}
import scala.xml.Utility.trim

class ValidationServiceSpec extends AnyWordSpec, BeforeAndAfterEach, Matchers, ScalaFutures, OptionValues:
  private val mockAppConfig = mock[AppConfig]

  private val validationService = ValidationService(mockAppConfig, new ValidatingXmlLoader)

  private val valid_soap_message: Elem =
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
                <v41:ReceiveReferenceDataRequestResult>c04a1612-705d-4373-8840-9d137b14b301</v41:ReceiveReferenceDataRequestResult>
            </v4:ReceiveReferenceDataReqMsg>
        </soap:Body>
    </soap:Envelope>

  private val valid_is_alive_soap_message: Elem =
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
                   xmlns:v1="http://xmlns.ec.eu/BusinessMessages/TATAFng/Monitoring/V1">
      <soap:Header>
        <Action xmlns="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.Default.CSRD.ReferenceDataExportReceiverCBS/IsAlive</Action>
        <MessageID xmlns="http://www.w3.org/2005/08/addressing">urn:uuid:fcb0896f-33d1-4542-8f64-1dce8101ca09</MessageID>
      </soap:Header>
      <soap:Body>
        <v1:isAliveReqMsg />
      </soap:Body>
    </soap:Envelope>

  private val invalid_soap_action_message: Elem =
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
                   xmlns:v1="http://xmlns.ec.eu/BusinessMessages/TATAFng/Monitoring/V1">
      <soap:Header>
        <Action xmlns="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.Default.CSRD.ReferenceDataSubmissionResultReceiverCBS/ReceiveReferenceDataSubmissionResult</Action>
        <MessageID xmlns="http://www.w3.org/2005/08/addressing">urn:uuid:fcb0896f-33d1-4542-8f64-1dce8101ca09</MessageID>
      </soap:Header>
      <soap:Body>
        <v1:isAliveReqMsg />
      </soap:Body>
    </soap:Envelope>

  private val valid_error_report_soap_message: Elem =
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

  private val invalid_soap_message: Elem =
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
                   xmlns:v4="http://xmlns.ec.eu/CallbackService/CSRD2/IReferenceDataExportReceiverCBS/V4"
                   xmlns:v41="http://xmlns.ec.eu/BusinessObjects/CSRD2/ReferenceDataExportReceiverCBSServiceType/V4"
                   xmlns:v2="http://xmlns.ec.eu/BusinessObjects/CSRD2/MessageHeaderType/V2">
      <soap:Header>
        <Action xmlns="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.Default.CSRD.ReferenceDataExportReceiverCBS/ReceiveReferenceData</Action>
        <MessageID xmlns="http://www.w3.org/2005/08/addressing">urn:uuid:fcb0896f-33d1-4542-8f64-1dce8101ca09</MessageID>
      </soap:Header>
      <ExtraNode>
        <Should>
          <Not>
            <Be>
              <Present/>
            </Be>
          </Not>
        </Should>
      </ExtraNode>
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
          <v41:ReceiveReferenceDataRequestResult>c04a1612-705d-4373-8840-9d137b14b301</v41:ReceiveReferenceDataRequestResult>
        </v4:ReceiveReferenceDataReqMsg>
      </soap:Body>
    </soap:Envelope>

  private val invalid_is_alive_soap_message: Elem =
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
                   xmlns:v1="http://xmlns.ec.eu/BusinessMessages/TATAFng/Monitoring/V1">
      <soap:Header>
        <Action xmlns="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.Default.CSRD.ReferenceDataExportReceiverCBS/IsAlive</Action>
        <MessageID xmlns="http://www.w3.org/2005/08/addressing">urn:uuid:fcb0896f-33d1-4542-8f64-1dce8101ca09</MessageID>
      </soap:Header>
      <soap:Body>
        <v1:isAliveReqMsg>
          <Should>
            <Not>
              <Be>
                <Present/>
              </Be>
            </Not>
          </Should>
        </v1:isAliveReqMsg>
      </soap:Body>
    </soap:Envelope>

  private val valid_soap_message_with_invalid_body: Elem =
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
            <ExtraNode>
              <Should>
                <Not>
                  <Be>
                    <Present/>
                  </Be>
                </Not>
              </Should>
            </ExtraNode>
          </v4:ReceiveReferenceDataReqMsg>
        </soap:Body>
      </soap:Envelope>

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

  override def beforeEach(): Unit =
    reset(mockAppConfig)

  "ValidationService" when {
    "validating SOAP message" should {
      "succeed when validating a good soap message" in {
        when(mockAppConfig.xsdValidation).thenReturn(true)
        val actual = validationService.validateSoapMessage(valid_soap_message.toString)
        actual.value.head shouldBe trim(valid_soap_message)
      }

      "succeed when validating a good is alive soap message" in {
        when(mockAppConfig.xsdValidation).thenReturn(true)
        val actual = validationService.validateSoapMessage(valid_is_alive_soap_message.toString)
        actual.value.head shouldBe trim(valid_is_alive_soap_message)
      }

      "succeed when validating a good error report soap message" in {
        when(mockAppConfig.xsdValidation).thenReturn(true)
        val actual = validationService.validateSoapMessage(valid_error_report_soap_message.toString)
        actual.value.head shouldBe trim(valid_error_report_soap_message)
      }

      "fail when validating a reference data soap message with unexpected extra elements" in {
        when(mockAppConfig.xsdValidation).thenReturn(true)
        validationService.validateSoapMessage(invalid_soap_message.toString) shouldBe None
      }

      "succeed when validating a reference soap message with unexpected extra elements when XSD validation is disabled" in {
        when(mockAppConfig.xsdValidation).thenReturn(false)
        validationService.validateSoapMessage(invalid_soap_message.toString) shouldBe Some(invalid_soap_message)
      }

      "fail when validating an is alive soap message with unexpected extra elements" in {
        when(mockAppConfig.xsdValidation).thenReturn(true)
        validationService.validateSoapMessage(invalid_is_alive_soap_message.toString) shouldBe None
      }

      "succeed when validating an is alive soap message with unexpected extra elements when XSD validation is disabled" in {
        when(mockAppConfig.xsdValidation).thenReturn(false)
        validationService.validateSoapMessage(invalid_is_alive_soap_message.toString) shouldBe Some(
          invalid_is_alive_soap_message
        )
      }

      "fail when validating a good soap message with missing details in the body" in {
        when(mockAppConfig.xsdValidation).thenReturn(true)
        validationService.validateSoapMessage(valid_soap_message_with_invalid_body.toString) shouldBe None
      }

      "succeed when validating a good soap message with missing details in the body when XSD validation is disabled" in {
        when(mockAppConfig.xsdValidation).thenReturn(false)
        validationService.validateSoapMessage(valid_soap_message_with_invalid_body.toString) shouldBe Some(
          valid_soap_message_with_invalid_body
        )
      }
    }

    "extracting SOAP Action" should {
      "succeed when given a valid reference data message" in {
        validationService.extractSoapAction(valid_soap_message) shouldBe Some(SoapAction.ReceiveReferenceData)
      }

      "succeed when given a valid isAlive message" in {
        validationService.extractSoapAction(valid_is_alive_soap_message) shouldBe Some(SoapAction.IsAlive)
      }

      "fail when given anything else" in {
        validationService.extractSoapAction(invalid_soap_action_message) shouldBe None
      }
    }

    "extracting inner message" should {
      "succeed when extracting data from a good reference data soap message" in {
        validationService.extractInnerMessage(valid_soap_message) shouldBe Some(trim(valid_inner_message))
      }

      "succeed when extracting data from a good error report soap message" in {
        validationService.extractInnerMessage(valid_error_report_soap_message) shouldBe Some(trim(valid_inner_message))
      }

      "fail when extracting data from a reference data soap message that is missing mandatory data" in {
        validationService.extractInnerMessage(valid_soap_message_with_invalid_body) shouldBe None
      }

      "fail when extracting data from any other kind of message" in {
        validationService.extractInnerMessage(valid_is_alive_soap_message) shouldBe None
      }
    }
  }
