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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories

import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.EISRequest
import uk.gov.hmrc.mongo.MongoComponent
import org.mongodb.scala.*
import org.mongodb.scala.result.DeleteResult
import org.mongodb.scala.model.*
import uk.gov.hmrc.mongo.workitem.*
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo
import org.mongodb.scala.model.Filters

import java.time.{Clock, Duration, Instant}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EISWorkItemRepository @Inject() (
  mongoComponent: MongoComponent,
  appConfig: AppConfig
)(using ec: ExecutionContext)
    extends WorkItemRepository[EISRequest](
      collectionName = "eis-request",
      mongoComponent = mongoComponent,
      itemFormat = EISRequest.EISRequestFormat,
      workItemFields = WorkItemFields.default,
      extraIndexes = Seq(
        IndexModel(
          Indexes.ascending(WorkItemFields.default.updatedAt),
          IndexOptions().expireAfter(appConfig.workItemRetentionPeriod.toMillis, TimeUnit.MILLISECONDS)
        )
      )
    ):
  override def inProgressRetryAfter: Duration = appConfig.pollerRetryAfter

  override def now(): Instant = Clock.systemUTC().instant()

  def deleteAll(): Future[DeleteResult] =
    collection.deleteMany(Filters.empty()).toFuture()

  def set(item: EISRequest): Future[WorkItem[EISRequest]] =
    pushNew(item, now(), _ => ToDo)
