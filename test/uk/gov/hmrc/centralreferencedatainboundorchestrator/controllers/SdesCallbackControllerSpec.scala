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

import scala.concurrent.ExecutionContext.Implicits.global
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers.*
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.audit.AuditHandler
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.{InvalidSDESNotificationError, MongoReadError, MongoWriteError, NoMatchingUIDInMongoError, Property, SdesCallbackResponse}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.services.SdesService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.Future
import scala.util.Success

class SdesCallbackControllerSpec extends AnyWordSpec, GuiceOneAppPerSuite, Matchers, BeforeAndAfterEach:

  given HeaderCarrier = HeaderCarrier()

  private val fakeRequest = FakeRequest("POST", "/services/crdl/callback")
  lazy val mockSdesService: SdesService = mock[SdesService]
  lazy val mockAuditHandler: AuditHandler = mock[AuditHandler]
  private val controller = new SdesCallbackController(mockSdesService, Helpers.stubControllerComponents(),mockAuditHandler)
  given mat: Materializer = app.injector.instanceOf[Materializer]

  private val auditSuccess = Future.successful(Success)

  private val validTestBody: SdesCallbackResponse = SdesCallbackResponse("FileProcessingFailure", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d", LocalDateTime.now(),
    Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))

  private val invalidTestBody: SdesCallbackResponse = SdesCallbackResponse("FileProcessingTest", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d", LocalDateTime.now(),
    Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))

  private val invalidUID: SdesCallbackResponse = SdesCallbackResponse("FileProcessingTest", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", "", LocalDateTime.now(),
    Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))


  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockSdesService)
  }

  "POST /services/crdl/callback" should {
    "accept a valid message" in {
      when(mockSdesService.processCallback(ArgumentMatchers.eq(validTestBody))(using any())).thenReturn(Future("some"))

      
      val result = controller.sdesCallback()(
        fakeRequest
          .withBody(validTestBody)
      )
      status(result) shouldBe ACCEPTED
      verify(mockSdesService, times(1)).auditMessageWrapperAndSdesPayload(any)(using any())
    }

    "fail if invalid message" in {
      when(mockSdesService.processCallback(ArgumentMatchers.eq(invalidTestBody))(using any())).thenReturn(Future.failed(InvalidSDESNotificationError("invalid")))

      val result = controller.sdesCallback()(
        fakeRequest
          .withBody(invalidTestBody)
      )
      status(result) shouldBe BAD_REQUEST
      verify(mockSdesService, times(1)).auditMessageWrapperAndSdesPayload(any)(using any())
    }

    "fail if no UID present" in {
      when(mockSdesService.processCallback(ArgumentMatchers.eq(invalidUID))(using any())).thenReturn(Future.failed(NoMatchingUIDInMongoError("not found")))

      val result = controller.sdesCallback()(
        fakeRequest
          .withBody(invalidUID)
      )
      status(result) shouldBe NOT_FOUND
      verify(mockSdesService, times(1)).auditMessageWrapperAndSdesPayload(any)(using any())
    }

    "fail if Mongo Read Error" in {
      when(mockSdesService.processCallback(ArgumentMatchers.eq(validTestBody))(using any())).thenReturn(Future.failed(MongoReadError("failed")))

      val result = controller.sdesCallback()(
        fakeRequest
          .withBody(validTestBody)
      )
      status(result) shouldBe INTERNAL_SERVER_ERROR
      verify(mockSdesService, times(1)).auditMessageWrapperAndSdesPayload(any)(using any())
    }

    "fail if Mongo Write Error" in {
      when(mockSdesService.processCallback(ArgumentMatchers.eq(validTestBody))(using any())).thenReturn(Future.failed(MongoWriteError("failed")))

      val result = controller.sdesCallback()(
        fakeRequest
          .withBody(validTestBody)
      )
      status(result) shouldBe INTERNAL_SERVER_ERROR
      verify(mockSdesService, times(1)).auditMessageWrapperAndSdesPayload(any)(using any())
    }

    "fail if Any other Error" in {
      when(mockSdesService.processCallback(ArgumentMatchers.eq(validTestBody))(using any())).thenReturn(Future.failed(Throwable("Internal Server Error")))

      val result = controller.sdesCallback()(
        fakeRequest
          .withBody(validTestBody)
      )
      status(result) shouldBe INTERNAL_SERVER_ERROR
      verify(mockSdesService, times(1)).auditMessageWrapperAndSdesPayload(any)(using any())
    }
    
  }
