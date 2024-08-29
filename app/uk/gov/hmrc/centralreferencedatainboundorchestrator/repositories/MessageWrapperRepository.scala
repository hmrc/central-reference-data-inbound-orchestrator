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

import com.google.inject.{Inject, Singleton}
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Indexes, Updates}
import play.api.Logging
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import org.mongodb.scala.*
import org.mongodb.scala.result.DeleteResult
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.MessageStatus.MessageStatus

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MessageWrapperRepository @Inject()(
                                     mongoComponent: MongoComponent,
                                     appConfig: AppConfig
                                   )(using ec: ExecutionContext) extends PlayMongoRepository[MessageWrapper](
  collectionName = "message-wrapper",
  mongoComponent = mongoComponent,
  domainFormat = MessageWrapper.mongoFormat,
  indexes = Seq(
    IndexModel(
      Indexes.ascending("uid"),
      IndexOptions().name("uidx").unique(true)
    ),
    IndexModel(
      Indexes.ascending("lastUpdated"),
      IndexOptions()
        .name("lastUpdatedIdx")
        .expireAfter(appConfig.cacheTtl, TimeUnit.DAYS)
    )
  ),
  replaceIndexes = true
), Logging:

  def insertMessageWrapper(uid: String,
                           payload: String,
                           status: MessageStatus)
                          (using ec: ExecutionContext): Future[Boolean] = {
    logger.info(s"Inserting a message wrapper in $collectionName table with uid: $uid")
    collection.insertOne(MessageWrapper(uid, payload, status))
      .head()
      .map(_ =>
        logger.info(s"Inserted a message wrapper in $collectionName table with uid: $uid")
        true
      )
      .recoverWith {
        case e =>
          logger.error(s"failed to insert message wrapper with uid: $uid into $collectionName table with ${e.getMessage}")
          Future.failed(MongoWriteError(s"failed to insert message wrapper with uid: $uid into $collectionName table with ${e.getMessage}"))
      }
  }

  def findByUid(uid: String)(using ec: ExecutionContext): Future[Option[MessageWrapper]] =
    collection.find(Filters.equal("uid", uid))
      .headOption()
      .recoverWith {
        case e =>
          logger.error(s"failed to retrieve message wrapper with uid: $uid in $collectionName table with ${e.getMessage}")
          Future.failed(MongoReadError(s"failed to retrieve message wrapper with uid: $uid in $collectionName table with ${e.getMessage}"))
      }

  def deleteAll(): Future[DeleteResult] =
    collection.deleteMany(Filters.empty()).toFuture()

  def updateStatus(uid: String, status: MessageStatus)(using ec: ExecutionContext): Future[Boolean] =
    collection.updateOne(
        Filters.equal("uid", uid),
        Updates.combine(
          Updates.set("status", status.toString),
          Updates.set("lastUpdated", Instant.now())
        )
      ).toFuture()
      .map(_.wasAcknowledged())
      .recoverWith {
        case e =>
          logger.error(s"failed to update message wrappers status with uid: $uid & status: $status in $collectionName table with ${e.getMessage}")
          Future.failed(MongoWriteError(s"failed to update message wrappers status with uid: $uid & status: $status in $collectionName table with ${e.getMessage}"))

      }