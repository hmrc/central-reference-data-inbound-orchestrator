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

import org.scalatest.RecoverMethods.recoverToExceptionIf
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.MessageStatus.Received
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.{MessageStatus, MongoReadError}
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class MessageWrapperRepositorySpec extends AnyWordSpec,
  MockitoSugar,
  GuiceOneAppPerSuite,
  CleanMongoCollectionSupport,
  Matchers:

  val appConfig: AppConfig = mock[AppConfig]
  val repository: MessageWrapperRepository = MessageWrapperRepository(mongoComponent, appConfig)

  "MessageWrapperRepository" should {
    "Delete all existing documents" in {
      repository.insertMessageWrapper(UUID.randomUUID().toString, "PAYLOAD", Received).futureValue
      repository.insertMessageWrapper(UUID.randomUUID().toString, "PAYLOAD", Received).futureValue
      repository.insertMessageWrapper(UUID.randomUUID().toString, "PAYLOAD", Received).futureValue
      repository.insertMessageWrapper(UUID.randomUUID().toString, "PAYLOAD", Received).futureValue
      repository.insertMessageWrapper(UUID.randomUUID().toString, "PAYLOAD", Received).futureValue
      repository.insertMessageWrapper(UUID.randomUUID().toString, "PAYLOAD", Received).futureValue
      
      repository.deleteAll().futureValue

      val countOfDocsAfter = repository.collection.countDocuments().head().futureValue

      countOfDocsAfter shouldBe 0
    }
  }


