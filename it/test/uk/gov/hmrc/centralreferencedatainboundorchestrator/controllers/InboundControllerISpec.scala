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

import helpers.InboundSoapMessage
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.DefaultBodyWritables.*
import play.api.libs.ws.WSClient
import play.api.test.Helpers.*

class InboundControllerISpec extends AnyWordSpec,
  Matchers,
  ScalaFutures,
  IntegrationPatience,
  GuiceOneServerPerSuite:

  private val wsClient = app.injector.instanceOf[WSClient]
  private val baseUrl  = s"http://localhost:$port"
  private val url = s"$baseUrl/central-reference-data-inbound-orchestrator/"

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .build()

  "POST / endpoint" should {
    "return created with a valid request" in {
      val response =
        wsClient
          .url(url)
          .addHttpHeaders(
            "x-files-included" -> "true",
            "Content-Type" -> "application/xml"
          )
          .post(InboundSoapMessage.xmlBody.toString)
          .futureValue

      response.status shouldBe ACCEPTED
    }

    "return bad request if the request does not contain all of the headers" in {
      val response =
        wsClient
          .url(url)
          .addHttpHeaders(
            "Content-Type" -> "application/xml"
          )
          .post(InboundSoapMessage.xmlBody.toString)
          .futureValue

      response.status shouldBe BAD_REQUEST
    }
  }