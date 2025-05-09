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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.config

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.FiniteDuration
import java.time.Duration
import play.api.Configuration
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

@Singleton
class AppConfig @Inject() (config: Configuration) extends ServicesConfig(config):

  val appName: String                         = config.get[String]("appName")
  val cacheTtl: Long                          = config.get[Long]("mongodb.timeToLiveInDays")
  val eisUrl: String                          = baseUrl("eis-api")
  val eisPath: String                         = config.get[String]("microservice.services.eis-api.path")
  val eisBearerToken: String                  = config.get[String]("microservice.services.eis-api.bearerToken")
  val pollerInitialDelay: FiniteDuration      = config.get[FiniteDuration]("poller.initial-delay")
  val pollerInterval: FiniteDuration          = config.get[FiniteDuration]("poller.interval")
  val pollerRetryAfter: Duration              = config.get[Duration]("poller.in-progress-retry-after")
  val maxRetryCount: Int                      = config.get[Int]("poller.max-retry-count")
  val startScheduler: Boolean                 = config.getOptional[Boolean]("poller.start-scheduler").getOrElse(false)
  val workItemRetentionPeriod: FiniteDuration = config.get[FiniteDuration]("poller.work-item-retention-period")
  val xsdValidation: Boolean                  = config.get[Boolean]("microservice.features.xsdValidation")
