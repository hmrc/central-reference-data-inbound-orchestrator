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

import play.api.http.Status.*
import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalactic.Prettifier.default
import org.scalatest.RecoverMethods.recoverToExceptionIf
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.connectors.EisConnector
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.MessageStatus.Received
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.MessageWrapperRepository
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.{MessageWrapper, Property, SdesCallbackResponse}
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.time.LocalDateTime
import scala.concurrent.Future

class SdesServiceSpec extends AnyWordSpec, GuiceOneAppPerSuite, Matchers, ScalaFutures:

  given scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  given HeaderCarrier = HeaderCarrier()

  lazy val mockMessageWrapperRepository: MessageWrapperRepository = mock[MessageWrapperRepository]
  lazy val mockEisConnector: EisConnector = mock[EisConnector]
  given mat: Materializer = app.injector.instanceOf[Materializer]

  private val sdesService = new SdesService(mockMessageWrapperRepository, mockEisConnector)

  private def messageWrapper(id: String) = MessageWrapper(id, "<Body/>", Received)

  "SdesService" should {
    "should return av scan passed when accepting a FileReceived notification" in {
      val uid = "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"
      when(mockMessageWrapperRepository.findByUid(any())(using any()))
        .thenReturn(Future.successful(Some(messageWrapper(uid))))

      when(mockMessageWrapperRepository.updateStatus(any(), any())(using any()))
        .thenReturn(Future.successful(true))

      when(mockEisConnector.forwardMessage(any())(using any(), any()))
        .thenReturn(Future.successful(HttpResponse(ACCEPTED, "response body")))

      val result = sdesService.processCallback(
        SdesCallbackResponse("FileReceived", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", uid, LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))
      ).futureValue

      result shouldBe "Message with UID: 32f2c4f7-c635-45e0-bee2-0bdd97a4a70d, successfully sent to EIS with 202 & status updated to sent"
    }

    "should return av scan failed when accepting a FileProcessingFailure notification" in {
      val uid = "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"
      when(mockMessageWrapperRepository.updateStatus(any(), any())(using any()))
        .thenReturn(Future.successful(true))

      val result = sdesService.processCallback(
        SdesCallbackResponse("FileProcessingFailure", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d", LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))
      ).futureValue

      result shouldBe "status updated to failed for uid: 32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"
    }

    "should fail if SDES notification is not valid" in {
      val uid = "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"

      val result = sdesService.processCallback(
        SdesCallbackResponse("FileProcessingTest", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d", LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))
      )

      recoverToExceptionIf[Throwable](result).map { rt =>
        rt.getMessage shouldBe "SDES notification not recognised: FileProcessingTest"
      }.futureValue
    }
  }



