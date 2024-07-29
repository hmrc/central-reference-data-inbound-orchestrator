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

import org.mockito.Mockito.when
import org.mongodb.scala.model.Filters
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import org.mockito.Mockito.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.MessageWrapper
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.MessageWrapperRepository
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.temporal.ChronoUnit
import java.time.{Clock, Instant, ZoneId}
import scala.concurrent.ExecutionContext.Implicits.global

class MessageWrapperRepositorySpec
  extends AnyFreeSpec
    , Matchers
    , DefaultPlayMongoRepositorySupport[MessageWrapper]
    , ScalaFutures
    , IntegrationPatience
    , OptionValues
    , MockitoSugar {

  private val instant = Instant.now.truncatedTo(ChronoUnit.MILLIS)
  private val stubClock: Clock = Clock.fixed(instant, ZoneId.systemDefault)

  private val messageWrapper = MessageWrapper("id", "<Body/>", "received")

  private val mockAppConfig = mock[AppConfig]
  when(mockAppConfig.cacheTtl) thenReturn 1.toLong

  val messageRepository = new MessageWrapperRepository(
    mongoComponent = mongoComponent,
    appConfig = mockAppConfig
  )
  override protected val repository: PlayMongoRepository[MessageWrapper] = messageRepository //unused

  ".insertMessageWrapper" - {

    "must insert a message wrapper successfully" in {

      val expectedResult = messageWrapper copy (lastUpdated = instant, receivedTimestamp = instant)

      val insertResult     = messageRepository.insertMessageWrapper(messageWrapper.uid, messageWrapper.payload, messageWrapper.status).futureValue
      val fetchedRecord    = find(Filters.equal("uid", messageWrapper.uid)).futureValue.headOption.value

      insertResult mustEqual true
      fetchedRecord.uid mustEqual expectedResult.uid
    }
  }

  ".findByUid" - {

    "must be able to retrieve a message wrapper successfully using a uid" in {

      val expectedResult = messageWrapper copy (lastUpdated = instant, receivedTimestamp = instant)

      val insertResult = messageRepository.insertMessageWrapper(messageWrapper.uid, messageWrapper.payload, messageWrapper.status).futureValue
      val fetchedRecord = messageRepository.findByUid(messageWrapper.uid).futureValue

      insertResult mustEqual true
      fetchedRecord.value.copy(lastUpdated = instant, receivedTimestamp = instant) mustEqual expectedResult
    }

    "must return none if uid not found" in {

      val insertResult = messageRepository.insertMessageWrapper("1234", messageWrapper.payload, messageWrapper.status).futureValue
      val fetchedRecord = messageRepository.findByUid(messageWrapper.uid).futureValue

      insertResult mustEqual true
      fetchedRecord must be(None)
    }
  }

  ".updateStatus" - {

    "must be able to update the status of an existing message wrapper" in {

      val insertResult = messageRepository.insertMessageWrapper(messageWrapper.uid, messageWrapper.payload, messageWrapper.status).futureValue
      val fetchedBeforeUpdateRecord = messageRepository.findByUid(messageWrapper.uid).futureValue
      val updatedRecord = messageRepository.updateStatus(messageWrapper.uid, "sent").futureValue
      val fetchedRecord = messageRepository.findByUid(messageWrapper.uid).futureValue

      insertResult mustEqual true
      fetchedBeforeUpdateRecord.value.status mustEqual "received"
      updatedRecord mustEqual true
      fetchedRecord.value.status mustEqual "sent"
    }

    "must return status unchanged if uid not found and updating status doesn't happen" in {

      val insertResult = messageRepository.insertMessageWrapper(messageWrapper.uid, messageWrapper.payload, messageWrapper.status).futureValue
      val updatedRecord = messageRepository.updateStatus("1234", "sent").futureValue
      val fetchedRecord = messageRepository.findByUid(messageWrapper.uid).futureValue

      insertResult mustEqual true
      updatedRecord mustEqual true
      fetchedRecord.value.status mustEqual "received"
    }
  }
}
