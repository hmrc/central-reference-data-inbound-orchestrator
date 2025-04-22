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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.controllers

import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.audit.AuditHandler
import uk.gov.hmrc.centralreferencedatainboundorchestrator.helpers.{InboundSoapMessage, OutboundSoapMessage}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.SoapAction.{IsAlive, ReceiveReferenceData}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.services.{InboundControllerService, ValidationService}
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.*

class InboundControllerSpec extends AnyWordSpec, GuiceOneAppPerSuite, BeforeAndAfterEach, Matchers:

  lazy val mockInboundService: InboundControllerService = mock[InboundControllerService]
  lazy val mockAuditHandler: AuditHandler               = mock[AuditHandler]
  lazy val mockValidationService: ValidationService     = mock[ValidationService]

  private val fakeRequest = FakeRequest("POST", "/")
  private val controller  = new InboundController(
    Helpers.stubControllerComponents(),
    mockInboundService,
    mockValidationService,
    mockAuditHandler
  )
  given mat: Materializer = app.injector.instanceOf[Materializer]

  private val auditSuccess = Future.successful(Success)

  private val validReferenceDataMessage  = InboundSoapMessage.valid_soap_message
  private val validIsAliveRequestMessage = InboundSoapMessage.valid_soap_is_alive_message

  private val validIsAliveResponseMessage = OutboundSoapMessage.valid_is_alive_response_message

  private val validTestBody: Elem = <MainMessage>
      <Body>
        <TaskIdentifier>780912</TaskIdentifier>
        <AttributeName>ReferenceData</AttributeName>
      	<MessageType>gZip</MessageType>
      	<IncludedBinaryObject>c04a1612-705d-4373-8840-9d137b14b30a</IncludedBinaryObject>
      	<MessageSender>CS/RD2</MessageSender>
      </Body>
    </MainMessage>

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuditHandler)
    reset(mockValidationService)
    reset(mockInboundService)

    when(mockAuditHandler.auditNewMessageWrapper(any)(any))
      .thenReturn(auditSuccess)
  }

  "POST /" should {
    "accept a valid ReceiveReferenceData message" in {
      when(mockValidationService.validateSoapMessage(any)).thenReturn(Some(validReferenceDataMessage))
      when(mockValidationService.extractSoapAction(any)).thenReturn(Some(ReceiveReferenceData))
      when(mockValidationService.extractInnerMessage(any)).thenReturn(Some(validTestBody))
      when(mockInboundService.processMessage(any())).thenReturn(Future.successful(true))

      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "x-files-included" -> "true",
            "Content-Type"     -> "application/xml"
          )
          .withBody(validTestBody)
      )
      status(result) shouldBe ACCEPTED

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
      verify(mockValidationService, times(1)).validateSoapMessage(any)
      verify(mockValidationService, times(1)).extractSoapAction(any)
      verify(mockValidationService, times(1)).extractInnerMessage(any)
      verify(mockInboundService, times(1)).processMessage(any)
    }

    "accept a valid isAliveReqMsg message" in {
      when(mockValidationService.validateSoapMessage(any)).thenReturn(Some(validIsAliveRequestMessage))
      when(mockValidationService.extractSoapAction(any)).thenReturn(Some(IsAlive))

      val result = controller
        .submit()(
          fakeRequest
            .withHeaders(
              "x-files-included" -> "true",
              "Content-Type"     -> "application/xml"
            )
            .withBody(validTestBody)
        )
        .run()
      status(result) shouldBe OK
      contentAsString(result) shouldBe validIsAliveResponseMessage.toString

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
      verify(mockValidationService, times(1)).validateSoapMessage(any)
      verify(mockValidationService, times(1)).extractSoapAction(any)
      verify(mockValidationService, times(0)).extractInnerMessage(any)
      verify(mockInboundService, times(0)).processMessage(any)
    }

    "return Bad Request if an invalid ReceiveReferenceData message is supplied" in {
      when(mockValidationService.validateSoapMessage(any)).thenReturn(None)
      when(mockInboundService.processMessage(any())).thenReturn(Future.successful(true))

      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "x-files-included" -> "true",
            "Content-Type"     -> "application/xml"
          )
          .withBody(validTestBody)
      )
      status(result) shouldBe BAD_REQUEST

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
      verify(mockValidationService, times(1)).validateSoapMessage(any)
      verify(mockValidationService, times(0)).extractSoapAction(any)
      verify(mockInboundService, times(0)).processMessage(any)
    }

    "return Bad Request if the x-files-included header is not present in a ReceiveReferenceData message" in {
      when(mockValidationService.validateSoapMessage(any)).thenReturn(Some(validReferenceDataMessage))
      when(mockValidationService.extractSoapAction(any)).thenReturn(Some(ReceiveReferenceData))

      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "Content-Type" -> "application/xml"
          )
          .withBody(validTestBody)
      )
      status(result) shouldBe BAD_REQUEST

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
      verify(mockValidationService, times(1)).validateSoapMessage(any)
      verify(mockValidationService, times(1)).extractSoapAction(any)
      verify(mockValidationService, times(0)).extractInnerMessage(any)
      verify(mockInboundService, times(0)).processMessage(any)
    }

    "return internal server error if process message fails with MongoWriteError" in {
      when(mockValidationService.validateSoapMessage(any)).thenReturn(Some(validReferenceDataMessage))
      when(mockValidationService.extractSoapAction(any)).thenReturn(Some(ReceiveReferenceData))
      when(mockValidationService.extractInnerMessage(any)).thenReturn(Some(validTestBody))
      when(mockInboundService.processMessage(any())).thenReturn(Future.failed(MongoWriteError("failed")))

      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "x-files-included" -> "true",
            "Content-Type"     -> "application/xml"
          )
          .withBody(validTestBody)
      )
      status(result) shouldBe INTERNAL_SERVER_ERROR

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
      verify(mockValidationService, times(1)).validateSoapMessage(any)
      verify(mockValidationService, times(1)).extractSoapAction(any)
      verify(mockValidationService, times(1)).extractInnerMessage(any)
      verify(mockInboundService, times(1)).processMessage(any)
    }

    "return internal server error if process message fails with MongoReadError" in {
      when(mockValidationService.validateSoapMessage(any)).thenReturn(Some(validReferenceDataMessage))
      when(mockValidationService.extractSoapAction(any)).thenReturn(Some(ReceiveReferenceData))
      when(mockValidationService.extractInnerMessage(any)).thenReturn(Some(validTestBody))
      when(mockInboundService.processMessage(any())).thenReturn(Future.failed(MongoReadError("failed")))

      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "x-files-included" -> "true",
            "Content-Type"     -> "application/xml"
          )
          .withBody(validTestBody)
      )
      status(result) shouldBe INTERNAL_SERVER_ERROR

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
      verify(mockValidationService, times(1)).validateSoapMessage(any)
      verify(mockValidationService, times(1)).extractSoapAction(any)
      verify(mockValidationService, times(1)).extractInnerMessage(any)
      verify(mockInboundService, times(1)).processMessage(any)
    }

    "return bad request if UID is missing in XML" in {
      when(mockValidationService.validateSoapMessage(any)).thenReturn(Some(validReferenceDataMessage))
      when(mockValidationService.extractSoapAction(any)).thenReturn(Some(ReceiveReferenceData))
      when(mockValidationService.extractInnerMessage(any)).thenReturn(Some(validTestBody))
      when(mockInboundService.processMessage(any())).thenReturn(Future.failed(InvalidXMLContentError("failed")))

      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "x-files-included" -> "true",
            "Content-Type"     -> "application/xml"
          )
          .withBody(validTestBody)
      )
      status(result) shouldBe BAD_REQUEST

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
      verify(mockValidationService, times(1)).validateSoapMessage(any)
      verify(mockValidationService, times(1)).extractSoapAction(any)
      verify(mockValidationService, times(1)).extractInnerMessage(any)
      verify(mockInboundService, times(1)).processMessage(any)
    }

    "fail if another error is thrown during message processing" in {
      when(mockValidationService.validateSoapMessage(any)).thenReturn(Some(validReferenceDataMessage))
      when(mockValidationService.extractSoapAction(any)).thenReturn(Some(ReceiveReferenceData))
      when(mockValidationService.extractInnerMessage(any)).thenReturn(Some(validTestBody))
      when(mockInboundService.processMessage(any())).thenReturn(Future.failed(Throwable("failed")))

      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "x-files-included" -> "true",
            "Content-Type"     -> "application/xml"
          )
          .withBody(validTestBody)
      )
      status(result) shouldBe INTERNAL_SERVER_ERROR

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
      verify(mockValidationService, times(1)).validateSoapMessage(any)
      verify(mockValidationService, times(1)).extractSoapAction(any)
      verify(mockValidationService, times(1)).extractInnerMessage(any)
      verify(mockInboundService, times(1)).processMessage(any)
    }
  }
