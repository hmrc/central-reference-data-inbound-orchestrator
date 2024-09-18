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

import org.apache.pekko.stream.Materializer
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalactic.Prettifier.default
import org.scalatest.RecoverMethods.recoverToExceptionIf
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.centralreferencedatainboundorchestrator.connectors.EisConnector
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.MessageStatus.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.{EISWorkItemRepository, MessageWrapperRepository}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.*
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}
import org.bson.types.ObjectId
import org.scalatest.BeforeAndAfterEach
import uk.gov.hmrc.centralreferencedatainboundorchestrator.audit.AuditHandler
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.play.audit.http.connector.AuditResult
import uk.gov.hmrc.play.audit.http.connector.AuditResult.Success

import java.time.LocalDateTime
import java.time.Instant
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.Elem

class SdesServiceSpec extends AnyWordSpec,
  GuiceOneAppPerSuite, BeforeAndAfterEach, Matchers, ScalaFutures:

  given ExecutionContext = ExecutionContext.global

  given HeaderCarrier = HeaderCarrier()

  lazy val mockMessageWrapperRepository: MessageWrapperRepository = mock[MessageWrapperRepository]
  lazy val mockEISWorkItemRepository: EISWorkItemRepository = mock[EISWorkItemRepository]
  lazy val mockEisConnector: EisConnector = mock[EisConnector]
  lazy val mockAuditHandler:AuditHandler = mock[AuditHandler]
  lazy val mockAppConfig: AppConfig = mock[AppConfig]

  when(mockAppConfig.maxRetryCount).thenReturn(3)

  given mat: Materializer = app.injector.instanceOf[Materializer]

  private val sdesService =
    new SdesService(
      mockMessageWrapperRepository,
      mockEISWorkItemRepository,
      mockEisConnector,
      mockAuditHandler,
      mockAppConfig
    )

  private val testBody: Elem = <Body></Body>

  private def messageWrapper(id: String) = MessageWrapper(id, testBody.toString, Received)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockMessageWrapperRepository,
      mockEISWorkItemRepository,
      mockEisConnector,
      mockAuditHandler
    )
  }

  "SdesService" should {
    "should return av scan passed when accepting a FileReceived notification" in {
      val uid = UUID.randomUUID().toString
      val message = messageWrapper(uid)

      when(mockMessageWrapperRepository.findByUid(eqTo(uid))(using any()))
        .thenReturn(Future.successful(Some(message)))

      val expectedRequest: EISRequest = EISRequest(message.payload, uid)

      val wi = WorkItem(
        new ObjectId(),
        Instant.now(),
        Instant.now(),
        Instant.now(),
        ProcessingStatus.ToDo,
        0,
        expectedRequest
      )

      when(mockEISWorkItemRepository.set(eqTo(expectedRequest)))
        .thenReturn(Future.successful(wi))

      val result = sdesService.processCallback(
        SdesCallbackResponse("FileReceived", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", uid, LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))
      ).futureValue

      result shouldBe s"Message with UID: $uid, successfully queued"

      verify(mockMessageWrapperRepository, times(0)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(1)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(1)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any)(using any(), any())
    }

    "should return av scan failed when accepting a FileProcessingFailure notification" in {
      val uid = "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"
      when(mockMessageWrapperRepository.updateStatus(eqTo(uid), eqTo(Failed))(using any()))
        .thenReturn(Future.successful(true))

      val result = sdesService.processCallback(
        SdesCallbackResponse("FileProcessingFailure", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", uid, LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))
      ).futureValue

      result shouldBe "status updated to failed for uid: 32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"

      verify(mockMessageWrapperRepository, times(1)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any)(using any(), any())
    }

    "should fail if SDES notification is not valid" in {
      val uid = "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"

      val result = sdesService.processCallback(
        SdesCallbackResponse("FileProcessingTest", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", uid, LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))
      )

      recoverToExceptionIf[Throwable](result).map { rt =>
        rt.getMessage shouldBe "SDES notification not recognised: FileProcessingTest"
      }.futureValue

      verify(mockMessageWrapperRepository, times(0)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any)(using any(), any())
    }

    "forward a payload to EIS" in {
      when(mockEisConnector.forwardMessage(eqTo(testBody))(using any(), any()))
        .thenReturn(Future.successful(true))

      val result = sdesService.sendMessage(testBody.toString).futureValue

      result shouldBe true

      verify(mockMessageWrapperRepository, times(0)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(1)).forwardMessage(any)(using any(), any())
    }

    "update wrapper status on successful call to EIS" in {
      val uid = UUID.randomUUID().toString

      when(mockMessageWrapperRepository.updateStatus(eqTo(uid), eqTo(Sent))(using any()))
        .thenReturn(Future.successful(true))

      val result = sdesService.updateStatus(true, uid).futureValue

      result shouldBe s"Message with UID: $uid, successfully sent to EIS and status updated to sent."

      verify(mockMessageWrapperRepository, times(1)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any)(using any(), any())
    }

    "update wrapper status on successful call to EIS with status update failure" in {
      val uid = UUID.randomUUID().toString

      when(mockMessageWrapperRepository.updateStatus(eqTo(uid), eqTo(Sent))(using any()))
        .thenReturn(Future.successful(false))

      val result = sdesService.updateStatus(true, uid)

      recoverToExceptionIf[MongoWriteError](result).map { mwe =>
        mwe.message shouldBe s"failed to update message wrappers status to failed with uid: $uid"
      }.futureValue

      verify(mockMessageWrapperRepository, times(1)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any)(using any(), any())
    }

    "update wrapper status on failed call to EIS" in {
      val uid = UUID.randomUUID().toString

      when(mockMessageWrapperRepository.updateStatus(eqTo(uid), eqTo(Sent))(using any()))
        .thenReturn(Future.successful(false))

      val result = sdesService.updateStatus(false, uid)

      recoverToExceptionIf[EisResponseError](result).map { mwe =>
        mwe.message shouldBe s"Unable to send message to EIS after 3 attempts"
      }.futureValue

      verify(mockMessageWrapperRepository, times(0)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any)(using any(), any())
    }

    "should return exception MongoWriteError when accepting a FileProcessingFailure notification but Mongo fails to write" in {
      val uid = "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"
      when(mockMessageWrapperRepository.updateStatus(eqTo(uid), eqTo(Failed))(using any()))
        .thenReturn(Future.successful(false))

      val result = sdesService.processCallback(
        SdesCallbackResponse("FileProcessingFailure", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", uid, LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))
      )

      recoverToExceptionIf[MongoWriteError](result).map { mwe =>
        mwe.message shouldBe s"failed to update message wrappers status to failed with uid: $uid"
      }.futureValue

      verify(mockMessageWrapperRepository, times(1)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any)(using any(), any())
    }

    "should return exception NoMatchingUIDInMongoError when forwarding a message but Mongo fails to find UID in Mongo Matching" in {
      val uid = UUID.randomUUID().toString
      val message = messageWrapper(uid)

      when(mockMessageWrapperRepository.findByUid(eqTo(uid))(using any()))
        .thenReturn(Future.successful(None))

      val result = sdesService.processCallback(
        SdesCallbackResponse("FileReceived", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", uid, LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))
      )

      recoverToExceptionIf[NoMatchingUIDInMongoError](result).map { mwe =>
        mwe.message shouldBe s"Failed to find a UID in Mongo matching: $uid"
      }.futureValue

      verify(mockMessageWrapperRepository, times(0)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(1)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any)(using any(), any())
    }

    "should create an audit with the Message Wrapper and Sdes Payload" in {
      val uid = UUID.randomUUID().toString
      val message = messageWrapper(uid)

      when(mockMessageWrapperRepository.findByUid(eqTo(uid))(using any()))
        .thenReturn(Future.successful(Some(message)))

      when(mockAuditHandler.auditNewMessageWrapper(any,any)(using any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = sdesService.auditMessageWrapperAndSdesPayload(
        SdesCallbackResponse("FileReceived", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", uid, LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))
      ).futureValue

      result shouldBe Success

      verify(mockMessageWrapperRepository, times(1)).findByUid(any)(using any())
      verify(mockAuditHandler, times(1)).auditNewMessageWrapper(any, any)(using any())
    }

    "should audit the Sdes Payload Only" in {
      val uid = UUID.randomUUID().toString

      when(mockMessageWrapperRepository.findByUid(eqTo(uid))(using any()))
        .thenReturn(Future.successful(None))

      when(mockAuditHandler.auditNewMessageWrapper(any, any)(using any()))
        .thenReturn(Future.successful(AuditResult.Success))

      val result = sdesService.auditMessageWrapperAndSdesPayload(
        SdesCallbackResponse("FileReceived", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", uid, LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))
      ).futureValue

      result shouldBe Success

      verify(mockMessageWrapperRepository, times(1)).findByUid(any)(using any())
      verify(mockAuditHandler,times(1)).auditNewMessageWrapper(any,any)(using any())
    }
    
  }



