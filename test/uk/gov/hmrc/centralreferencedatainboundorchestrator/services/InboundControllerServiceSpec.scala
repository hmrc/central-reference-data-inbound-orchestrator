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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.RecoverMethods.recoverToExceptionIf
import org.scalatestplus.mockito.MockitoSugar.mock
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.MessageWrapperRepository
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.*

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.SoapAction.{ReferenceDataExport, ReferenceDataSubscription}

import scala.concurrent.Future
import scala.xml.NodeBuffer

class InboundControllerServiceSpec extends AnyWordSpec, Matchers, ScalaFutures:

  lazy val mockMessageWrapperRepository: MessageWrapperRepository = mock[MessageWrapperRepository]
  private val controller                                          = new InboundControllerService(mockMessageWrapperRepository)

  private val validTestBody = <MainMessage>
    <Body>
      <TaskIdentifier>780912</TaskIdentifier>
      <AttributeName>ReferenceData</AttributeName>
      <MessageType>gZip</MessageType>
      <IncludedBinaryObject>c04a1612-705d-4373-8840-9d137b14b30a</IncludedBinaryObject>
      <MessageSender>CS/RD2</MessageSender>
    </Body>
  </MainMessage>

  private val invalidTestBody = <MainMessage>
    <Body>
      <TaskIdentifier>780912</TaskIdentifier>
      <AttributeName>ReferenceData</AttributeName>
      <MessageType>gZip</MessageType>
      <MessageSender>CS/RD2</MessageSender>
    </Body>
  </MainMessage>

  private val validSubscriptionErrorMessage = <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
                                                     xmlns:csrd="http://xmlns.ec.eu/CallbackService/CSRD2/IReferenceDataSubscriptionReceiverCBS/V4"
                                                     xmlns:msg="http://xmlns.ec.eu/BusinessObjects/CSRD2/ReferenceDataSubscriptionReceiverCBSServiceType/V4"
                                                     xmlns:hdr="http://xmlns.ec.eu/BusinessObjects/CSRD2/MessageHeaderType/V2">
    <soap:Header>
      <wsa:Action xmlns:wsa="http://www.w3.org/2005/08/addressing">
        CCN2.Service.Customs.Default.CSRD.ReferenceDataSubscriptionReceiverCBS/ReceiveReferenceData
      </wsa:Action>
      <wsa:MessageID xmlns:wsa="http://www.w3.org/2005/08/addressing">
        uuid:444852eb-fd52-4705-a9c4-e860b05ccd52
      </wsa:MessageID>
    </soap:Header>
    <soap:Body>
      <csrd:ReceiveReferenceDataReqMsg>
        <msg:ReceiveReferenceDataRequestType>
          <msg:MessageHeader>
            <hdr:MessageID>MSG-2024-12-30-003</hdr:MessageID>
            <hdr:MessageTimestamp>2024-12-30T12:00:00Z</hdr:MessageTimestamp>
            <hdr:SenderID>CUSTOMS_AUTHORITY_FR</hdr:SenderID>
            <hdr:ReceiverID>SUBSCRIBER_SYSTEM_03</hdr:ReceiverID>
          </msg:MessageHeader>
          <msg:ErrorReport>eSBmb3JtYXQu</msg:ErrorReport>
        </msg:ReceiveReferenceDataRequestType>
      </csrd:ReceiveReferenceDataReqMsg>
    </soap:Body>
  </soap:Envelope>

  private val invalidSubscriptionErrorMessage = <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
    <soap:Header>
      <wsa:Action xmlns:wsa="http://www.w3.org/2005/08/addressing">
        CCN2.Service.Customs.Default.CSRD.ReferenceDataSubscriptionReceiverCBS/ReceiveReferenceData
      </wsa:Action>
    </soap:Header>
    <soap:Body>
      <csrd:ReceiveReferenceDataReqMsg>
        <msg:ReceiveReferenceDataRequestType>
          <msg:MessageHeader>
            <hdr:MessageID>MSG-2024-12-30-003</hdr:MessageID>
          </msg:MessageHeader>
        </msg:ReceiveReferenceDataRequestType>
      </csrd:ReceiveReferenceDataReqMsg>
    </soap:Body>
  </soap:Envelope>

  "processMessage" should {
    "return true when retrieving UID from XML and storing xml message in Mongo successfully for ReferenceDataExport" in {
      when(mockMessageWrapperRepository.insertMessageWrapper(any(), any(), any(), any())(using any()))
        .thenReturn(Future.successful(true))

      val result = controller.processMessage(validTestBody, ReferenceDataExport).futureValue

      result shouldBe true
    }

    "return true when retrieving UUID from MessageID and storing xml message in Mongo successfully for ReferenceDataSubscription" in {
      when(mockMessageWrapperRepository.insertMessageWrapper(any(), any(), any(), any())(using any()))
        .thenReturn(Future.successful(true))

      val result = controller.processMessage(validSubscriptionErrorMessage, ReferenceDataSubscription).futureValue

      result shouldBe true
    }

    "return MongoWriteError when failing to store message in Mongo for ReferenceDataExport" in {
      when(mockMessageWrapperRepository.insertMessageWrapper(any(), any(), any(), any())(using any()))
        .thenReturn(Future.failed(MongoWriteError("failed")))

      val result = controller.processMessage(validTestBody, ReferenceDataExport)

      recoverToExceptionIf[Throwable](result).map { rt =>
        rt.getMessage shouldBe "failed"
      }
    }

    "return MongoWriteError when failing to store message in Mongo for ReferenceDataSubscription" in {
      when(mockMessageWrapperRepository.insertMessageWrapper(any(), any(), any(), any())(using any()))
        .thenReturn(Future.failed(MongoWriteError("failed")))

      val result = controller.processMessage(validSubscriptionErrorMessage, ReferenceDataSubscription)

      recoverToExceptionIf[Throwable](result).map { rt =>
        rt.getMessage shouldBe "failed"
      }
    }

    "throw an exception if UID is missing in XML for ReferenceDataExport" in {
      when(mockMessageWrapperRepository.insertMessageWrapper(any(), any(), any(), any())(using any()))
        .thenReturn(Future.failed(MongoWriteError("failed")))

      val result = controller.processMessage(invalidTestBody, ReferenceDataExport)

      recoverToExceptionIf[Throwable](result).map { rt =>
        rt.getMessage shouldBe "Failed to find UID in xml - potentially an error report"
      }.futureValue
    }

    "throw an exception if UUID is missing in MessageID for ReferenceDataSubscription" in {
      when(mockMessageWrapperRepository.insertMessageWrapper(any(), any(), any(), any())(using any()))
        .thenReturn(Future.failed(MongoWriteError("failed")))

      val result = controller.processMessage(invalidSubscriptionErrorMessage, ReferenceDataSubscription)

      recoverToExceptionIf[Throwable](result).map { rt =>
        rt.getMessage shouldBe "Failed to find UUID in MessageID"
      }.futureValue
    }
  }
