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

import ch.qos.logback.classic.Level
import org.apache.pekko.actor.ActorSystem
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Logger}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.EISRequest
import uk.gov.hmrc.centralreferencedatainboundorchestrator.module.OrchestratorModule
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.EISWorkItemRepository
import uk.gov.hmrc.mongo.TimestampSupport
import uk.gov.hmrc.mongo.lock.LockRepository
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.*
import uk.gov.hmrc.mongo.workitem.WorkItem
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing

import java.time.{Duration, Instant}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class OrchestratorPollerSpec extends AnyWordSpec, GuiceOneAppPerSuite, BeforeAndAfterEach, Matchers, LogCapturing:

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .disable[OrchestratorModule]
      .build()

  lazy val workItemRepository: EISWorkItemRepository = mock[EISWorkItemRepository]
  lazy val lockRepository: LockRepository            = mock[LockRepository]
  lazy val timestampSupport: TimestampSupport        = mock[TimestampSupport]
  lazy val sdesService: SdesService                  = mock[SdesService]
  lazy val appConfig: AppConfig                      = mock[AppConfig]

  lazy val defaultRetryInterval: Duration = Duration.ofMinutes(1)
  when(appConfig.startScheduler).thenReturn(false)
  when(appConfig.pollerRetryAfter).thenReturn(defaultRetryInterval)
  when(appConfig.maxRetryCount).thenReturn(3)

  val correlationID: String   = "correlationID"
  val testRequest: EISRequest = EISRequest("Test Payload", correlationID)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(sdesService)
  }

  private val now    = Instant.now()
  private val before = now.minus(defaultRetryInterval)

  private val poller = StubOrchestratorPoller(
    app.actorSystem,
    lockRepository,
    workItemRepository,
    timestampSupport,
    sdesService,
    appConfig
  )

  when(workItemRepository.now()).thenReturn(now)

  "Orchestrator Poller" should {
    "retrieve an outstanding items when none available" in {
      when(workItemRepository.pullOutstanding(eqTo(before), eqTo(now)))
        .thenReturn(Future.successful(None))

      withCaptureOfLoggingFrom(poller.testLogger) { logEvents =>
        poller.poller()
        syncLogs()
        logEvents.count(_.getLevel == Level.DEBUG) shouldBe 1
      }

      verify(sdesService, times(0)).sendMessage(any)(using any)
    }

    "processing a new item works" in {
      val wi = WorkItem[EISRequest](
        new ObjectId(),
        now,
        now,
        now,
        ToDo,
        0,
        testRequest
      )
      when(workItemRepository.pullOutstanding(eqTo(before), eqTo(now)))
        .thenReturn(Future.successful(Some(wi)))

      when(sdesService.sendMessage(eqTo(wi.item.payload))(using any))
        .thenReturn(Future.successful(true))

      withCaptureOfLoggingFrom(poller.testLogger) { logEvents =>
        poller.poller()
        syncLogs()
        logEvents.count(event =>
          event.getLevel == Level.INFO &&
            event.getFormattedMessage == "Successfully sent message"
        ) shouldBe 1
      }

      verify(sdesService, times(1)).sendMessage(any)(using any)
      verify(sdesService, times(1)).updateStatus(eqTo(true), eqTo(correlationID))
      verify(workItemRepository, times(1)).completeAndDelete(eqTo(wi.id))
    }

    "processing a new item works when it fails to send" in {
      val wi = WorkItem[EISRequest](
        new ObjectId(),
        now,
        now,
        now,
        ToDo,
        0,
        testRequest
      )
      when(workItemRepository.pullOutstanding(eqTo(before), eqTo(now)))
        .thenReturn(Future.successful(Some(wi)))

      when(sdesService.sendMessage(eqTo(wi.item.payload))(using any))
        .thenReturn(Future.successful(false))

      withCaptureOfLoggingFrom(poller.testLogger) { logEvents =>
        poller.poller()
        syncLogs()
        logEvents.count(event =>
          event.getLevel == Level.WARN &&
            event.getFormattedMessage == s"failed to send work item `${wi.id}` for correlation Id `correlationID`"
        ) shouldBe 1
      }

      verify(sdesService, times(1)).sendMessage(any)(using any)
      verify(sdesService, times(0)).updateStatus(any, any)
      verify(workItemRepository, times(1)).markAs(eqTo(wi.id), eqTo(Failed), any)
    }

    "processing a new item works when it fails to send too often" in {
      val wi = WorkItem[EISRequest](
        new ObjectId(),
        now,
        now,
        now,
        ToDo,
        3,
        testRequest
      )
      when(workItemRepository.pullOutstanding(eqTo(before), eqTo(now)))
        .thenReturn(Future.successful(Some(wi)))

      when(sdesService.sendMessage(eqTo(wi.item.payload))(using any))
        .thenReturn(Future.successful(false))

      withCaptureOfLoggingFrom(poller.testLogger) { logEvents =>
        poller.poller()
        syncLogs()
        logEvents.foreach(println)
        logEvents.count(event =>
          event.getLevel == Level.ERROR &&
            event.getFormattedMessage == s"failed to send work item `${wi.id}` 4 times. For correlation Id `correlationID`"
        ) shouldBe 1
      }

      verify(sdesService, times(1)).sendMessage(any)(using any)
      verify(sdesService, times(1)).updateStatus(eqTo(false), eqTo(correlationID))
      verify(workItemRepository, times(1))
        .markAs(eqTo(wi.id), eqTo(PermanentlyFailed), any)
    }

    "processing a new item works fails for some reason" in {
      val wi = WorkItem[EISRequest](
        new ObjectId(),
        now,
        now,
        now,
        ToDo,
        0,
        testRequest
      )
      when(workItemRepository.pullOutstanding(eqTo(before), eqTo(now)))
        .thenReturn(Future.successful(Some(wi)))

      when(sdesService.sendMessage(eqTo(wi.item.payload))(using any))
        .thenReturn(Future.failed(new IllegalArgumentException))

      withCaptureOfLoggingFrom(poller.testLogger) { logEvents =>
        poller.poller()
        syncLogs()
        logEvents.count(event =>
          event.getLevel == Level.ERROR &&
            event.getFormattedMessage == "We got an error processing an item"
        ) shouldBe 1
      }

      verify(sdesService, times(1)).sendMessage(any)(using any)
      verify(sdesService, times(0)).updateStatus(any, any)
      verify(workItemRepository, times(1)).markAs(eqTo(wi.id), eqTo(Failed), any)
    }

    "processing a new item throws an exception" in {
      val wi = WorkItem[EISRequest](
        new ObjectId(),
        now,
        now,
        now,
        ToDo,
        0,
        testRequest
      )
      when(workItemRepository.pullOutstanding(eqTo(before), eqTo(now)))
        .thenReturn(Future.successful(Some(wi)))

      when(sdesService.sendMessage(eqTo(wi.item.payload))(using any))
        .thenThrow(new IllegalArgumentException)

      withCaptureOfLoggingFrom(poller.testLogger) { logEvents =>
        poller.poller()
        syncLogs()
        logEvents.count(event =>
          event.getLevel == Level.ERROR &&
            event.getFormattedMessage == "We got an exception java.lang.IllegalArgumentException"
        ) shouldBe 1
      }

      verify(sdesService, times(1)).sendMessage(any)(using any)
      verify(sdesService, times(0)).updateStatus(any, any)
      verify(workItemRepository, times(1)).markAs(eqTo(wi.id), eqTo(Failed), any)
    }

    "processing a new item throws an exception on the final attempt" in {
      val wi = WorkItem[EISRequest](
        new ObjectId(),
        now,
        now,
        now,
        ToDo,
        3,
        testRequest
      )
      when(workItemRepository.pullOutstanding(eqTo(before), eqTo(now)))
        .thenReturn(Future.successful(Some(wi)))

      when(sdesService.sendMessage(eqTo(wi.item.payload))(using any))
        .thenThrow(new IllegalArgumentException)

      withCaptureOfLoggingFrom(poller.testLogger) { logEvents =>
        poller.poller()
        syncLogs()
        logEvents.count(event =>
          event.getLevel == Level.ERROR &&
            event.getFormattedMessage == "We got an exception java.lang.IllegalArgumentException"
        ) shouldBe 1
      }

      verify(sdesService, times(1)).sendMessage(any)(using any)
      verify(sdesService, times(1)).updateStatus(eqTo(false), eqTo(correlationID))
      verify(workItemRepository, times(1)).markAs(eqTo(wi.id), eqTo(PermanentlyFailed), any)
    }
  }

  /** The logging is asynchronous so we need a short pause to allow the log capturing to catch up
    */
  def syncLogs(): Unit = Thread.sleep(20)

// A test stub to expose the logger.
class StubOrchestratorPoller(
  actorSystem: ActorSystem,
  lockRepository: LockRepository,
  workItemRepo: EISWorkItemRepository,
  timestampSupport: TimestampSupport,
  sdesService: SdesService,
  appConfig: AppConfig
)(using ec: ExecutionContext)
    extends OrchestratorPoller(
      actorSystem,
      lockRepository,
      workItemRepo,
      timestampSupport,
      sdesService,
      appConfig
    ):
  val testLogger: Logger = logger
