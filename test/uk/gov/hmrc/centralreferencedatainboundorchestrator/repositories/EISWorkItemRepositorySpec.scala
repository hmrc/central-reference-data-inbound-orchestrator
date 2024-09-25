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

import org.mockito.Mockito.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.EISRequest
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.*

class EISWorkItemRepositorySpec extends AnyWordSpec,
  MockitoSugar,
  GuiceOneAppPerSuite,
  CleanMongoCollectionSupport,
  Matchers:

  val appConfig: AppConfig = mock[AppConfig]

  when(appConfig.workItemRetentionPeriod).thenReturn(5.days)

  val repository: EISWorkItemRepository = EISWorkItemRepository(mongoComponent, appConfig)
  
  def testRequest: EISRequest = EISRequest("payload", UUID.randomUUID().toString)

  override given patienceConfig: PatienceConfig = PatienceConfig(
    timeout = scaled(Span(15, Seconds)),
    interval = scaled(Span(15, Seconds))
  )

  "EIS Work Item Repository" should {
    "Add a new EIS Request" in {
      val countOfDocsBefore = documentCount()

      repository.set(testRequest).futureValue

      val countOfDocsAfter = documentCount()
      
      countOfDocsBefore + 1 shouldBe countOfDocsAfter
    }
    
    "Delete all existing documents" in {
      repository.set(testRequest).futureValue
      repository.set(testRequest).futureValue
      repository.set(testRequest).futureValue
      repository.set(testRequest).futureValue
      repository.set(testRequest).futureValue
      repository.set(testRequest).futureValue

      repository.deleteAll().futureValue

      val countOfDocsAfter = documentCount()

      countOfDocsAfter shouldBe 0      
    }
  }

  def documentCount(): Long = repository.collection.countDocuments().head().futureValue
