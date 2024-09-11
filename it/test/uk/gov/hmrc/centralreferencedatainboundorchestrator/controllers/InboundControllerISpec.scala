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

import com.github.tomakehurst.wiremock.client.WireMock.*
import helpers.InboundSoapMessage
import org.mongodb.scala.SingleObservableFuture
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.DefaultBodyWritables.*
import play.api.libs.ws.WSClient
import play.api.test.Helpers.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.MessageWrapperRepository
import uk.gov.hmrc.http.test.ExternalWireMockSupport
import uk.gov.hmrc.mongo.test.MongoSupport

class InboundControllerISpec extends AnyWordSpec,
  Matchers,
  ScalaFutures,
  IntegrationPatience,
  MongoSupport,
  ExternalWireMockSupport,
  GuiceOneServerPerSuite,
  BeforeAndAfterEach,
  BeforeAndAfterAll:

  private val wsClient = app.injector.instanceOf[WSClient]
  private val baseUrl  = s"http://localhost:$port"
  private val url = s"$baseUrl/central-reference-data-inbound-orchestrator/"

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "auditing.consumer.baseUri.host"  -> externalWireMockHost,
        "auditing.consumer.baseUri.port"  -> s"$externalWireMockPort",
        "auditing.enabled"                -> "true",
        "mongodb.uri"                     -> s"$mongoUri"
      )
      .build()

  lazy val messageWrapperRepository: MessageWrapperRepository = app.injector.instanceOf[MessageWrapperRepository]

  override def beforeEach(): Unit = {
    await(mongoDatabase.drop().toFuture())
    await(messageWrapperRepository.ensureIndexes())
  }

  override def afterAll(): Unit = {
    await(mongoDatabase.drop().toFuture())
  }

  "POST / endpoint" should {
    "return Accepted with a valid request" in {
      stubFor(
        post(urlEqualTo("/write/audit"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )

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
      stubFor(
        post(urlEqualTo("/write/audit"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )

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

    "return Internal server error when two valid request with the same UID" in {
      stubFor(
        post(urlEqualTo("/write/audit"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )

      val response =
        wsClient
          .url(url)
          .addHttpHeaders(
            "x-files-included" -> "true",
            "Content-Type" -> "application/xml"
          )
          .post(InboundSoapMessage.xmlBody.toString)
          .futureValue

      val responseDuplicate =
        wsClient
          .url(url)
          .addHttpHeaders(
            "x-files-included" -> "true",
            "Content-Type" -> "application/xml"
          )
          .post(InboundSoapMessage.xmlBody.toString)
          .futureValue

      response.status shouldBe ACCEPTED
      responseDuplicate.status shouldBe INTERNAL_SERVER_ERROR
    }
  }
