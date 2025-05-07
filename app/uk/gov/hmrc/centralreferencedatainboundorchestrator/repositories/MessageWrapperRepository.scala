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
import com.mongodb.client.model.{FindOneAndUpdateOptions, ReturnDocument}
import org.mongodb.scala.*
import org.mongodb.scala.model.*
import org.mongodb.scala.result.DeleteResult
import play.api.Logging
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.MessageStatus.MessageStatus
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.Clock
import java.util.concurrent.TimeUnit
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class MessageWrapperRepository @Inject() (
  mongoComponent: MongoComponent,
  appConfig: AppConfig,
  clock: Clock
)(using ec: ExecutionContext)
    extends PlayMongoRepository[MessageWrapper](
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
    ),
      Logging:

  def insertMessageWrapper(uid: String, payload: String, status: MessageStatus)(using
    ec: ExecutionContext
  ): Future[Boolean] = {
    val currentInstant = clock.instant()
    logger.info(s"Inserting a message wrapper in $collectionName table with uid: $uid")
    collection
      .insertOne(MessageWrapper(uid, payload, status, currentInstant, currentInstant))
      .head()
      .map(_ =>
        logger.info(s"Inserted a message wrapper in $collectionName table with uid: $uid")
        true
      )
      .recoverWith { case e =>
        logger.error(s"Failed to insert message wrapper with ID $uid", e)
        Future.failed(
          MongoWriteError(s"Failed to insert message wrapper with ID $uid", e)
        )
      }
  }

  def findByUid(uid: String)(using ec: ExecutionContext): Future[Option[MessageWrapper]] =
    collection
      .find(Filters.equal("uid", uid))
      .headOption()
      .recoverWith { case e =>
        logger.error(s"Failed to retrieve message wrapper with ID $uid", e)
        Future.failed(
          MongoReadError(s"Failed to retrieve message wrapper with ID $uid", e)
        )
      }

  def findByUidAndUpdateStatus(uid: String, expectedStatus: MessageStatus, newStatus: MessageStatus)(using
    ec: ExecutionContext
  ): Future[Option[MessageWrapper]] =
    collection
      .findOneAndUpdate(
        Filters.and(
          Filters.equal("uid", uid),
          Filters.equal("status", expectedStatus.toString)
        ),
        Updates.combine(
          Updates.set("status", newStatus.toString),
          Updates.set("lastUpdated", clock.instant())
        ),
        FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
      )
      .headOption()
      .recoverWith { case e =>
        logger.error(s"Failed to retrieve and update message wrapper with ID $uid to status $newStatus", e)
        Future.failed(
          MongoWriteError(s"Failed to retrieve and update message wrapper with ID $uid to status $newStatus", e)
        )
      }

  def deleteAll(): Future[DeleteResult] =
    collection.deleteMany(Filters.empty()).toFuture()

  def updateStatus(uid: String, expectedStatus: MessageStatus, newStatus: MessageStatus)(using
    ec: ExecutionContext
  ): Future[Unit] =
    collection
      .updateOne(
        Filters.and(Filters.equal("uid", uid), Filters.equal("status", expectedStatus.toString)),
        Updates.combine(
          Updates.set("status", newStatus.toString),
          Updates.set("lastUpdated", clock.instant())
        )
      )
      .toFuture()
      .transformWith {
        case Success(result) =>
          if !result.wasAcknowledged() then {
            logger.error(s"Failed to update message wrapper with ID $uid to status $newStatus")
            Future.failed(MongoWriteError(s"Failed to update message wrapper with ID $uid to status $newStatus"))
          } else if result.getModifiedCount > 0 then {
            Future.successful(s"Message wrapper with ID $uid updated")
          } else {
            Future.failed(
              NoMatchingUIDInMongoError(s"Failed to find a message wrapper with ID $uid and status $expectedStatus")
            )
          }
        case Failure(e)      =>
          logger.error(s"Failed to update message wrapper with ID $uid to status $newStatus", e)
          Future.failed(
            MongoWriteError(s"Failed to update message wrapper status with ID $uid to status $newStatus", e)
          )
      }
