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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalactic.Prettifier.default
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers.await
import play.api.test.Helpers.defaultAwaitTimeout
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.{Property, SdesCallback}

import java.time.LocalDateTime

class SdesServiceSpec extends AnyWordSpec, GuiceOneAppPerSuite, Matchers:

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  given mat: Materializer = app.injector.instanceOf[Materializer]

  val sdesService = new SdesService
  "SdesService" should {
    "should return av scan passed when accepting a FileReceived notification" in {
      val result = sdesService.processCallback(
        SdesCallback("FileReceived", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d", LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))
      )
       await(result) shouldBe "AV Scan passed"
    }

    "should return av scan failed when accepting a FileProcessingFailure notification" in {
      val result = sdesService.processCallback(
        SdesCallback("FileProcessingFailure", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d", LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))
      )
      await(result) shouldBe "AV Scan failed"
    }
  }



