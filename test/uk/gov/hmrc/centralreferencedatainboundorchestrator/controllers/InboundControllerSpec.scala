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
import uk.gov.hmrc.centralreferencedatainboundorchestrator.services.InboundControllerService
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.*

class InboundControllerSpec extends AnyWordSpec, GuiceOneAppPerSuite, BeforeAndAfterEach, Matchers:

  lazy val mockInboundService: InboundControllerService = mock[InboundControllerService]
  lazy val mockAuditHandler: AuditHandler = mock[AuditHandler]

  private val fakeRequest = FakeRequest("POST", "/")
  private val controller = new InboundController(Helpers.stubControllerComponents(), mockInboundService, mockAuditHandler)
  given mat: Materializer = app.injector.instanceOf[Materializer]

  private val auditSuccess = Future.successful(Success)

  // This is the expected body we need to send to EIS, using this for test purposes
  // until we get a real sample input file.
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

    when(mockAuditHandler.auditNewMessageWrapper(any)(any))
      .thenReturn(auditSuccess)
  }

  "POST /" should {
    "accept a valid message" in {
      when(mockInboundService.processMessage(any()))
        .thenReturn(Future.successful(true))

      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "x-files-included" -> "true",
            "Content-Type" -> "application/xml"
          )
          .withBody(validTestBody)
      )
      status(result) shouldBe ACCEPTED

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
    }

    "return Bad Request if the x-files-included header is not present" in {
      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "Content-Type" -> "application/xml"
          )
          .withBody(validTestBody)
      )
      status(result) shouldBe BAD_REQUEST

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
    }

    "return Bad Request if there is no XML content" in {
      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "x-files-included" -> "true"
          )
      )
      status(result) shouldBe UNSUPPORTED_MEDIA_TYPE
    }

    "return internal server error if process message fails" in {
      when(mockInboundService.processMessage(any()))
        .thenReturn(Future.failed(MongoWriteError("failed")))

      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "x-files-included" -> "true",
            "Content-Type" -> "application/xml"
          )
          .withBody(validTestBody)
      )
      status(result) shouldBe INTERNAL_SERVER_ERROR

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
    }

    "return internal server error if reading process message fails" in {
      when(mockInboundService.processMessage(any()))
        .thenReturn(Future.failed(MongoReadError("failed")))

      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "x-files-included" -> "true",
            "Content-Type" -> "application/xml"
          )
          .withBody(validTestBody)
      )
      status(result) shouldBe INTERNAL_SERVER_ERROR
      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
      
    }

    "return bad request if UID is missing in XML" in {
      when(mockInboundService.processMessage(any()))
        .thenReturn(Future.failed(InvalidXMLContentError("failed")))

      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "x-files-included" -> "true",
            "Content-Type" -> "application/xml"
          )
          .withBody(validTestBody)
      )
      status(result) shouldBe BAD_REQUEST

      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
    }

    "fail if Any other Error" in {
      when(mockInboundService.processMessage(any()))
        .thenReturn(Future.failed(Throwable("failed")))

      val result = controller.submit()(
        fakeRequest
          .withHeaders(
            "x-files-included" -> "true",
            "Content-Type" -> "application/xml"
          )
          .withBody(validTestBody)
      )
      status(result) shouldBe INTERNAL_SERVER_ERROR
      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any)(any)
    }
    
    
  }