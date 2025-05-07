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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlEqualTo}
import org.mongodb.scala.SingleObservableFuture
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.DefaultBodyWritables.*
import play.api.libs.ws.JsonBodyWritables.*
import play.api.libs.ws.{WSClient, writeableOf_NodeSeq}
import play.api.test.Helpers.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.helpers.InboundSoapMessage
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.{Property, SdesCallbackResponse}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.MessageWrapperRepository
import uk.gov.hmrc.http.test.ExternalWireMockSupport
import uk.gov.hmrc.mongo.test.MongoSupport

import java.time.LocalDateTime

class SdesCallbackControllerISpec extends AnyWordSpec,
  Matchers,
  ScalaFutures,
  IntegrationPatience,
  MongoSupport,
  ExternalWireMockSupport,
  GuiceOneServerPerSuite:

  private val wsClient = app.injector.instanceOf[WSClient]
  private val messageWrapperRepository = app.injector.instanceOf[MessageWrapperRepository]
  private val baseUrl  = s"http://localhost:$port"
  private val wrapperUrl = s"$baseUrl/central-reference-data-inbound-orchestrator/"
  private val callbackUrl = s"$baseUrl/central-reference-data-inbound-orchestrator/services/crdl/callback"
  private val uid = "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"

  private val validTestBody: SdesCallbackResponse = SdesCallbackResponse("FileProcessingFailure", s"$uid.zip", uid, LocalDateTime.now(),
    Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))

  private val invalidTestBody: SdesCallbackResponse = SdesCallbackResponse("FileProcessingTest", s"$uid.zip", uid, LocalDateTime.now(),
    Option("894bed34007114b82fa39e05197f9eec"), Option("MD5"), Option(LocalDateTime.now()), List(Property("name1", "value1")), Option("None"))

  private def sdesNotification(notificationType: String) =
    SdesCallbackResponse(
      notificationType,
      "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip",
      "c04a1612-705d-4373-8840-9d137b14b301",
      LocalDateTime.now(),
      Option("894bed34007114b82fa39e05197f9eec"),
      Option("MD5"),
      Option(LocalDateTime.now()),
      List.empty[Property],
      Option("None")
    )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "auditing.consumer.baseUri.host" -> externalWireMockHost,
        "auditing.consumer.baseUri.port" -> s"$externalWireMockPort",
        "auditing.enabled" -> "true",
        "mongodb.uri" -> s"$mongoUri"
      )
      .build()

  override def beforeEach(): Unit = {
    await(mongoDatabase.drop().toFuture())
    await(messageWrapperRepository.ensureIndexes())
  }

  override def afterAll(): Unit = {
    await(mongoDatabase.drop().toFuture())
  }

  "POST /services/crdl/callback endpoint" should {
    "return created with a valid request" in {
      stubFor(
        post(urlEqualTo("/write/audit"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )

      // Create a message wrapper
      val wrapperResponse =
        wsClient
          .url(wrapperUrl)
          .addHttpHeaders("x-files-included" -> "true")
          .post(InboundSoapMessage.valid_soap_message_with_id(uid))
          .futureValue

      wrapperResponse.status shouldBe ACCEPTED

      // Send ScanFailed for that message wrapper
      val callbackResponse =
        wsClient
          .url(callbackUrl)
          .addHttpHeaders("Content-Type" -> "application/json")
          .post(Json.toJson(validTestBody).toString)
          .futureValue

      callbackResponse.status shouldBe ACCEPTED
    }

    "return unsupported media type if the request does not contain all of the headers" in {

      stubFor(
        post(urlEqualTo("/write/audit"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )

      val response =
        wsClient
          .url(callbackUrl)
          .post(Json.toJson(validTestBody).toString)
          .futureValue

      response.status shouldBe UNSUPPORTED_MEDIA_TYPE
    }

    "return bad request when an invalid request is received" in {

      stubFor(
        post(urlEqualTo("/write/audit"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )

      val response =
        wsClient
          .url(callbackUrl)
          .addHttpHeaders(
            "Content-Type" -> "application/json"
          )
          .post(Json.toJson(invalidTestBody).toString)
          .futureValue

      response.status shouldBe BAD_REQUEST
    }

    "reject FileProcessed notification when the wrapper has not yet been scanned" in {
      stubFor(
        post(urlEqualTo("/write/audit"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )

      val wrapperResponse =
        wsClient
          .url(wrapperUrl)
          .addHttpHeaders(
            "x-files-included" -> "true",
            "Content-Type" -> "application/xml"
          )
          .post(InboundSoapMessage.valid_soap_message.toString)
          .futureValue

      wrapperResponse.status shouldBe ACCEPTED

      val processingNotifResponse =
        wsClient
          .url(callbackUrl)
          .post(Json.toJson(sdesNotification("FileProcessed")))
          .futureValue

      processingNotifResponse.status shouldBe NOT_FOUND
    }

    "reject duplicate AV scanning notifications" in {
      stubFor(
        post(urlEqualTo("/write/audit"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )

      val wrapperResponse =
        wsClient
          .url(wrapperUrl)
          .addHttpHeaders(
            "x-files-included" -> "true",
            "Content-Type" -> "application/xml"
          )
          .post(InboundSoapMessage.valid_soap_message.toString)
          .futureValue

      wrapperResponse.status shouldBe ACCEPTED

      val firstScanFailedNotifResponse =
        wsClient
          .url(callbackUrl)
          .post(Json.toJson(sdesNotification("FileProcessingFailure")))
          .futureValue

      firstScanFailedNotifResponse.status shouldBe ACCEPTED

      val secondScanFailedNotifResponse =
        wsClient
          .url(callbackUrl)
          .post(Json.toJson(sdesNotification("FileProcessingFailure")))
          .futureValue

      secondScanFailedNotifResponse.status shouldBe NOT_FOUND
    }

    "reject FileProcessed notification when the wrapper failed AV scan" in {
      stubFor(
        post(urlEqualTo("/write/audit"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )

      val wrapperResponse =
        wsClient
          .url(wrapperUrl)
          .addHttpHeaders(
            "x-files-included" -> "true",
            "Content-Type" -> "application/xml"
          )
          .post(InboundSoapMessage.valid_soap_message.toString)
          .futureValue

      wrapperResponse.status shouldBe ACCEPTED

      val scanFailedNotifResponse =
        wsClient
          .url(callbackUrl)
          .post(Json.toJson(sdesNotification("FileProcessingFailure")))
          .futureValue

      scanFailedNotifResponse.status shouldBe ACCEPTED

      val processingNotifResponse =
        wsClient
          .url(callbackUrl)
          .post(Json.toJson(sdesNotification("FileProcessed")))
          .futureValue

      processingNotifResponse.status shouldBe NOT_FOUND
    }

    "reject duplicate FileProcessed notifications" in {
      stubFor(
        post(urlEqualTo("/write/audit"))
          .willReturn(
            aResponse()
              .withStatus(NO_CONTENT)
          )
      )

      val wrapperResponse =
        wsClient
          .url(wrapperUrl)
          .addHttpHeaders(
            "x-files-included" -> "true",
            "Content-Type" -> "application/xml"
          )
          .post(InboundSoapMessage.valid_soap_message.toString)
          .futureValue

      wrapperResponse.status shouldBe ACCEPTED

      val scanPassedNotifResponse =
        wsClient
          .url(callbackUrl)
          .post(Json.toJson(sdesNotification("FileReceived")))
          .futureValue

      scanPassedNotifResponse.status shouldBe ACCEPTED

      val firstProcessedNotifResponse =
        wsClient
          .url(callbackUrl)
          .post(Json.toJson(sdesNotification("FileProcessed")))
          .futureValue

      firstProcessedNotifResponse.status shouldBe ACCEPTED

      val secondProcessedNotifResponse =
        wsClient
          .url(callbackUrl)
          .post(Json.toJson(sdesNotification("FileProcessed")))
          .futureValue

      secondProcessedNotifResponse.status shouldBe NOT_FOUND
    }
  }
