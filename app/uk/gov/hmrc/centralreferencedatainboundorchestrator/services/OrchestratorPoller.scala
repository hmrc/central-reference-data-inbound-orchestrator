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

import org.apache.pekko.actor.{ActorSystem, Cancellable}
import play.api.Logging
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.EISRequest
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.EISWorkItemRepository
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}

import java.time.Duration
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

@Singleton
class OrchestratorPoller @Inject()(
                                    actorSystem: ActorSystem,
                                    workItemRepo: EISWorkItemRepository,
                                    sdesService: SdesService,
                                    appConfig: AppConfig
                                  ) (using ec: ExecutionContext) extends Logging:

  private val initialDelay: FiniteDuration = appConfig.pollerInitialDelay
  private val interval: FiniteDuration = appConfig.pollerInterval
  private val retryAfter: Duration = appConfig.pollerRetryAfter

  given hc: HeaderCarrier = HeaderCarrier()

  val _: Cancellable = if appConfig.startScheduler then
    actorSystem.scheduler.
      scheduleAtFixedRate(initialDelay, interval)(run())(ec)
  else
    Cancellable.alreadyCancelled

  private[services] def run(): Runnable = () => {
    poller()
  }

  private[services] def poller(): Unit ={
    val items = workItemRepo.pullOutstanding(
      failedBefore = workItemRepo.now().minus(retryAfter),
      availableBefore = workItemRepo.now()
    )
    items.flatMap {
      case None =>
        logger.info("We did not find any requests")
        Future.unit
      case Some(wi) =>
        try {
          sdesService.sendMessage(wi.item.payload) transform {
            case Success(true) =>
              logger.info("Successfully sent message")
              sdesService.updateStatus(true, wi.item.correlationID)
              Success(workItemRepo.completeAndDelete(wi.id))
            case Success(false) if wi.failureCount < appConfig.maxRetryCount =>
              logger.warn(s"failed to send work item `${wi.id}` for correlation Id `${wi.item.correlationID}`")
              Success(failedAttempt(wi))
            case Success(false) =>
              logger.error(s"failed to send work item `${wi.id}` ${wi.failureCount + 1} times. For correlation Id `${wi.item.correlationID}`")
              Success(failedAttempt(wi))
            case Failure(err) =>
              logger.error(s"We got an error $err")
              Success(failedAttempt(wi))
          }
        } catch {
          case e: Exception =>
            logger.error("We got an error processing an item", e)
            failedAttempt(wi)
        }
    }
  }

  private def failedAttempt(wi: WorkItem[EISRequest]): Future[Boolean] = {
    if wi.failureCount < appConfig.maxRetryCount then
      workItemRepo.markAs(wi.id, ProcessingStatus.Failed)
    else
      sdesService.updateStatus(false, wi.item.correlationID)
      workItemRepo.markAs(wi.id, ProcessingStatus.PermanentlyFailed)
  }
