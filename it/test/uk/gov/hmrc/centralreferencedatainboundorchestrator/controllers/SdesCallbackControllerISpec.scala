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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.controllers

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.DefaultBodyWritables.*
import play.api.libs.ws.WSClient
import play.api.test.Helpers.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.{Property, SdesCallback}

import java.time.LocalDateTime
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.SdesCallback.*

class SdesCallbackControllerISpec extends AnyWordSpec,
  Matchers,
  ScalaFutures,
  IntegrationPatience,
  GuiceOneServerPerSuite:

  private val wsClient = app.injector.instanceOf[WSClient]
  private val baseUrl  = s"http://localhost:$port"
  private val url = s"$baseUrl/central-reference-data-inbound-orchestrator/services/crdl/callback"

  private val validTestBody: SdesCallback = SdesCallback("FileProcessingFailure", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip", "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d", LocalDateTime.now(),
    Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))


  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .build()

  "POST /services/crdl/callback endpoint" should {
    "return created with a valid request" in {

      val response =
        wsClient
          .url(url)
          .addHttpHeaders(
            "Content-Type" -> "application/json"
          )
          .post(Json.toJson(validTestBody).toString)
          .futureValue

      response.status shouldBe ACCEPTED
    }

    "return bad request if the request does not contain all of the headers" in {
      val response =
        wsClient
          .url(url)
          .post(Json.toJson(validTestBody).toString)
          .futureValue

      response.status shouldBe UNSUPPORTED_MEDIA_TYPE
    }
  }