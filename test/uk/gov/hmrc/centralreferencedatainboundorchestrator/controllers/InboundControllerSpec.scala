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
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.SoapAction.{IsAlive, ReferenceDataExport, ReferenceDataSubscription}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.EISWorkItemRepository
import uk.gov.hmrc.centralreferencedatainboundorchestrator.services.{InboundControllerService, ValidationService}
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.*

class InboundControllerSpec extends AnyWordSpec, GuiceOneAppPerSuite, BeforeAndAfterEach, Matchers:

  lazy val mockInboundService: InboundControllerService = mock[InboundControllerService]
  lazy val mockAuditHandler: AuditHandler               = mock[AuditHandler]
  lazy val mockValidationService: ValidationService     = mock[ValidationService]
  lazy val mockWorkItemRepo: EISWorkItemRepository      = mock[EISWorkItemRepository]

  import uk.gov.hmrc.mongo.workitem.WorkItem

  private val fakeRequest = FakeRequest("POST", "/")
  private val controller  = new InboundController(
    Helpers.stubControllerComponents(),
    mockInboundService,
    mockValidationService,
    mockAuditHandler,
    mockWorkItemRepo
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

  private val subscriptionMessageWithRDEntityList: NodeSeq =
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
      <soap:Header>
        <MessageID>uuid:12345678-1234-1234-1234-123456789abc</MessageID>
      </soap:Header>
      <soap:Body>
        <ReceiveReferenceDataRequestType>
          <RDEntityList>
            <Entity>EntityData</Entity>
          </RDEntityList>
        </ReceiveReferenceDataRequestType>
      </soap:Body>
    </soap:Envelope>

  private val subscriptionMessageWithErrorReport: NodeSeq =
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
      <soap:Header>
        <MessageID>uuid:12345678-1234-1234-1234-123456789abc</MessageID>
      </soap:Header>
      <soap:Body>
        <ReceiveReferenceDataRequestType>
          <ErrorReport>
            <Error>Error details</Error>
          </ErrorReport>
        </ReceiveReferenceDataRequestType>
      </soap:Body>
    </soap:Envelope>

  private val subscriptionMessageWithoutEither: NodeSeq =
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
      <soap:Header>
        <MessageID>uuid:12345678-1234-1234-1234-123456789abc</MessageID>
      </soap:Header>
      <soap:Body>
        <ReceiveReferenceDataRequestType>
          <SomeOtherElement>Data</SomeOtherElement>
        </ReceiveReferenceDataRequestType>
      </soap:Body>
    </soap:Envelope>

  private val subscriptionMessageWithoutUUID: NodeSeq =
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
      <soap:Header>
      </soap:Header>
      <soap:Body>
        <ReceiveReferenceDataRequestType>
          <RDEntityList>
            <Entity>EntityData</Entity>
          </RDEntityList>
        </ReceiveReferenceDataRequestType>
      </soap:Body>
    </soap:Envelope>

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuditHandler)
    reset(mockValidationService)
    reset(mockInboundService)
    reset(mockWorkItemRepo)

    when(mockAuditHandler.auditNewMessageWrapper(any)(any))
      .thenReturn(auditSuccess)
  }

  "POST /" should {
    "accept a valid ReferenceDataExport message" in {
      when(mockValidationService.validateAndExtractAction(any))
        .thenReturn(Some((ReferenceDataExport, validReferenceDataMessage)))
      when(mockValidationService.extractInnerMessage(any)).thenReturn(Some(validTestBody))
      when(mockInboundService.processMessage(any(), any())).thenReturn(Future.successful(true))

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
      verify(mockValidationService, times(1)).validateAndExtractAction(any)
      verify(mockValidationService, times(1)).extractInnerMessage(any)
      verify(mockInboundService, times(1)).processMessage(any, any)
    }

    "accept a valid isAliveReqMsg message" in {
      when(mockValidationService.validateAndExtractAction(any))
        .thenReturn(Some((IsAlive, validIsAliveRequestMessage)))

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
      verify(mockValidationService, times(1)).validateAndExtractAction(any)
      verify(mockValidationService, times(0)).extractInnerMessage(any)
      verify(mockInboundService, times(0)).processMessage(any, any)
    }

    "handle ReferenceDataSubscription message with RDEntityList" in {
      when(mockValidationService.validateAndExtractAction(any))
        .thenReturn(Some((ReferenceDataSubscription, subscriptionMessageWithRDEntityList)))
      when(mockWorkItemRepo.set(any[EISRequest]))
        .thenReturn(Future.successful(mock[WorkItem[EISRequest]]))

      val result = controller.submit()(
        fakeRequest
          .withHeaders("Content-Type" -> "application/xml")
          .withBody(subscriptionMessageWithRDEntityList.toString)
      )

      status(result)        shouldBe OK
      contentAsString(result) should include("12345678-1234-1234-1234-123456789abc")
      contentAsString(result) should include("successfully queued")

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
      verify(mockValidationService, times(1)).validateAndExtractAction(any)
      verify(mockWorkItemRepo, times(1)).set(any[EISRequest])
      verify(mockInboundService, times(0)).processMessage(any, any)
    }

    "handle ReferenceDataSubscription message with ErrorReport" in {
      when(mockValidationService.validateAndExtractAction(any))
        .thenReturn(Some((ReferenceDataSubscription, subscriptionMessageWithErrorReport)))
      when(mockInboundService.processMessage(any(), any()))
        .thenReturn(Future.successful(true))

      val result = controller.submit()(
        fakeRequest
          .withHeaders("Content-Type" -> "application/xml")
          .withBody(subscriptionMessageWithErrorReport.toString)
      )

      status(result) shouldBe BAD_REQUEST

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
      verify(mockValidationService, times(1)).validateAndExtractAction(any)
      verify(mockWorkItemRepo, times(0)).set(any[EISRequest])
    }

    "return Bad Request for ReferenceDataSubscription without RDEntityList or ErrorReport" in {
      when(mockValidationService.validateAndExtractAction(any))
        .thenReturn(Some((ReferenceDataSubscription, subscriptionMessageWithoutEither)))

      val result = controller.submit()(
        fakeRequest
          .withHeaders("Content-Type" -> "application/xml")
          .withBody(subscriptionMessageWithoutEither.toString)
      )

      status(result)        shouldBe BAD_REQUEST
      contentAsString(result) should include("Payload must contain either RDEntityList or ErrorReport")

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
      verify(mockValidationService, times(1)).validateAndExtractAction(any)
      verify(mockWorkItemRepo, times(0)).set(any[EISRequest])
      verify(mockInboundService, times(0)).processMessage(any, any)
    }

    "return Bad Request for ReferenceDataSubscription with RDEntityList but missing UUID" in {
      when(mockValidationService.validateAndExtractAction(any))
        .thenReturn(Some((ReferenceDataSubscription, subscriptionMessageWithoutUUID)))

      val result = controller.submit()(
        fakeRequest
          .withHeaders("Content-Type" -> "application/xml")
          .withBody(subscriptionMessageWithoutUUID.toString)
      )

      status(result)        shouldBe BAD_REQUEST
      contentAsString(result) should include("Missing or invalid UUID in MessageID")

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
      verify(mockValidationService, times(1)).validateAndExtractAction(any)
      verify(mockWorkItemRepo, times(0)).set(any[EISRequest])
      verify(mockInboundService, times(0)).processMessage(any, any)
    }

    "return Bad Request if an invalid message is supplied" in {
      when(mockValidationService.validateAndExtractAction(any)).thenReturn(None)
      when(mockInboundService.processMessage(any(), any())).thenReturn(Future.successful(true))

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
      verify(mockValidationService, times(1)).validateAndExtractAction(any)
      verify(mockInboundService, times(0)).processMessage(any, any)
    }

    "return Bad Request if the x-files-included header is not present in a ReferenceDataExport message" in {
      when(mockValidationService.validateAndExtractAction(any))
        .thenReturn(Some((ReferenceDataExport, validReferenceDataMessage)))

      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "Content-Type" -> "application/xml"
          )
          .withBody(validTestBody)
      )
      status(result) shouldBe BAD_REQUEST

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
      verify(mockValidationService, times(1)).validateAndExtractAction(any)
      verify(mockValidationService, times(0)).extractInnerMessage(any)
      verify(mockInboundService, times(0)).processMessage(any, any)
    }

    "return internal server error if process message fails with MongoWriteError" in {
      when(mockValidationService.validateAndExtractAction(any))
        .thenReturn(Some((ReferenceDataExport, validReferenceDataMessage)))
      when(mockValidationService.extractInnerMessage(any)).thenReturn(Some(validTestBody))
      when(mockInboundService.processMessage(any(), any())).thenReturn(Future.failed(MongoWriteError("failed")))

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
      verify(mockValidationService, times(1)).validateAndExtractAction(any)
      verify(mockValidationService, times(1)).extractInnerMessage(any)
      verify(mockInboundService, times(1)).processMessage(any, any)
    }

    "return internal server error if process message fails with MongoReadError" in {
      when(mockValidationService.validateAndExtractAction(any))
        .thenReturn(Some((ReferenceDataExport, validReferenceDataMessage)))
      when(mockValidationService.extractInnerMessage(any)).thenReturn(Some(validTestBody))
      when(mockInboundService.processMessage(any(), any())).thenReturn(Future.failed(MongoReadError("failed")))

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
      verify(mockValidationService, times(1)).validateAndExtractAction(any)
      verify(mockValidationService, times(1)).extractInnerMessage(any)
      verify(mockInboundService, times(1)).processMessage(any, any)
    }

    "return bad request if UID is missing in XML" in {
      when(mockValidationService.validateAndExtractAction(any))
        .thenReturn(Some((ReferenceDataExport, validReferenceDataMessage)))
      when(mockValidationService.extractInnerMessage(any)).thenReturn(Some(validTestBody))
      when(mockInboundService.processMessage(any(), any())).thenReturn(Future.failed(InvalidXMLContentError("failed")))

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
      verify(mockValidationService, times(1)).validateAndExtractAction(any)
      verify(mockValidationService, times(1)).extractInnerMessage(any)
      verify(mockInboundService, times(1)).processMessage(any, any)
    }

    "fail if another error is thrown during message processing" in {
      when(mockValidationService.validateAndExtractAction(any))
        .thenReturn(Some((ReferenceDataExport, validReferenceDataMessage)))
      when(mockValidationService.extractInnerMessage(any)).thenReturn(Some(validTestBody))
      when(mockInboundService.processMessage(any(), any())).thenReturn(Future.failed(Throwable("failed")))

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
      verify(mockValidationService, times(1)).validateAndExtractAction(any)
      verify(mockValidationService, times(1)).extractInnerMessage(any)
      verify(mockInboundService, times(1)).processMessage(any, any)
    }
  }

  "extractUuid" should {

    "extract valid UUID with uuid: prefix" in {
      val soapMessage =
        <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
          <soap:Header>
            <MessageID>uuid:123e4567-e89b-12d3-a456-426614174000</MessageID>
          </soap:Header>
        </soap:Envelope>

      val method = controller.getClass.getDeclaredMethod("extractUuid", classOf[NodeSeq])
      method.setAccessible(true)
      method.invoke(controller, soapMessage) shouldBe Some("123e4567-e89b-12d3-a456-426614174000")
    }

    "return None when UUID is invalid or missing" in {
      val soapMessage =
        <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
          <soap:Header>
            <MessageID>uuid:invalid-uuid-format</MessageID>
          </soap:Header>
        </soap:Envelope>

      val method = controller.getClass.getDeclaredMethod("extractUuid", classOf[NodeSeq])
      method.setAccessible(true)
      method.invoke(controller, soapMessage) shouldBe None
    }

    "return None when MessageID is empty" in {
      val soapMessage =
        <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
          <soap:Header>
            <MessageID></MessageID>
          </soap:Header>
        </soap:Envelope>

      val method = controller.getClass.getDeclaredMethod("extractUuid", classOf[NodeSeq])
      method.setAccessible(true)
      method.invoke(controller, soapMessage) shouldBe None
    }

    "return None when MessageID has less than 32 (UUID.length) characters" in {
      val soapMessage =
        <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope">
          <soap:Header>
            <MessageID>uuid:123</MessageID>
          </soap:Header>
        </soap:Envelope>

      val method = controller.getClass.getDeclaredMethod("extractUuid", classOf[NodeSeq])
      method.setAccessible(true)
      method.invoke(controller, soapMessage) shouldBe None
    }
  }
