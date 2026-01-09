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
import org.mongodb.scala.{ObservableFuture, SingleObservableFuture}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.DefaultBodyWritables.*
import play.api.libs.ws.WSClient
import play.api.libs.ws.readableAsXml
import play.api.test.Helpers.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.helpers.{InboundSoapMessage, OutboundSoapMessage}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.{EISWorkItemRepository, MessageWrapperRepository}
import uk.gov.hmrc.http.test.ExternalWireMockSupport
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.mongo.workitem.ProcessingStatus

import scala.xml.Elem

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
  lazy val workItemRepository: EISWorkItemRepository = app.injector.instanceOf[EISWorkItemRepository]

  private val subscriptionMessageWithRDEntityList: String =
    """<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
      |               xmlns:csrd="http://xmlns.ec.eu/CallbackService/CSRD2/IReferenceDataSubscriptionReceiverCBS/V4"
      |               xmlns:msg="http://xmlns.ec.eu/BusinessObjects/CSRD2/ReferenceDataSubscriptionReceiverCBSServiceType/V4"
      |               xmlns:hdr="http://xmlns.ec.eu/BusinessObjects/CSRD2/MessageHeaderType/V2"
      |               xmlns:rdlist="http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntityEntryListType/V3"
      |               xmlns:rdentity="http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntityType/V3"
      |               xmlns:rdentry="http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntryType/V3"
      |               xmlns:rdstatus="http://xmlns.ec.eu/BusinessObjects/CSRD2/RDStatusType/V3"
      |               xmlns:lsd="http://xmlns.ec.eu/BusinessObjects/CSRD2/LsdListType/V2">
      |  <soap:Header>
      |    <wsa:Action xmlns:wsa="http://www.w3.org/2005/08/addressing">
      |      CCN2.Service.Customs.Default.CSRD2.ReferenceDataSubscriptionReceiverCBS/ReceiveReferenceData
      |    </wsa:Action>
      |    <wsa:MessageID xmlns:wsa="http://www.w3.org/2005/08/addressing">
      |      uuid:12345678-1234-1234-1234-123456789012
      |    </wsa:MessageID>
      |  </soap:Header>
      |  <soap:Body>
      |    <csrd:ReceiveReferenceDataReqMsg>
      |      <msg:ReceiveReferenceDataRequestType>
      |        <msg:MessageHeader>
      |          <hdr:MessageID>MSG-2024-12-30-001</hdr:MessageID>
      |          <hdr:MessageTimestamp>2024-12-30T10:30:00Z</hdr:MessageTimestamp>
      |          <hdr:SenderID>CUSTOMS_AUTHORITY_UK</hdr:SenderID>
      |          <hdr:ReceiverID>SUBSCRIBER_SYSTEM_01</hdr:ReceiverID>
      |        </msg:MessageHeader>
      |        <msg:RDEntityList>
      |          <rdlist:RDEntity>
      |            <rdentity:name>CountryCode</rdentity:name>
      |            <rdentity:version>2024.1</rdentity:version>
      |            <rdentity:RDEntry>
      |              <rdentry:RDEntryStatus>
      |                <rdstatus:status>Active</rdstatus:status>
      |                <rdstatus:validFrom>2024-01-01</rdstatus:validFrom>
      |              </rdentry:RDEntryStatus>
      |              <rdentry:dataItem name="code">GB</rdentry:dataItem>
      |              <rdentry:dataItem name="numericCode">826</rdentry:dataItem>
      |              <rdentry:dataItem name="alpha3Code">GBR</rdentry:dataItem>
      |              <rdentry:LsdList>
      |                <lsd:Lsd>
      |                  <lsd:languageCode>EN</lsd:languageCode>
      |                  <lsd:description>United Kingdom</lsd:description>
      |                </lsd:Lsd>
      |                <lsd:Lsd>
      |                  <lsd:languageCode>FR</lsd:languageCode>
      |                  <lsd:description>Royaume-Uni</lsd:description>
      |                </lsd:Lsd>
      |              </rdentry:LsdList>
      |            </rdentity:RDEntry>
      |          </rdlist:RDEntity>
      |        </msg:RDEntityList>
      |      </msg:ReceiveReferenceDataRequestType>
      |    </csrd:ReceiveReferenceDataReqMsg>
      |  </soap:Body>
      |</soap:Envelope>""".stripMargin

  private val subscriptionMessageWithErrorReport: String =
    """<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
      |               xmlns:csrd="http://xmlns.ec.eu/CallbackService/CSRD2/IReferenceDataSubscriptionReceiverCBS/V4"
      |               xmlns:msg="http://xmlns.ec.eu/BusinessObjects/CSRD2/ReferenceDataSubscriptionReceiverCBSServiceType/V4"
      |               xmlns:hdr="http://xmlns.ec.eu/BusinessObjects/CSRD2/MessageHeaderType/V2">
      |  <soap:Header>
      |    <wsa:Action xmlns:wsa="http://www.w3.org/2005/08/addressing">
      |      CCN2.Service.Customs.Default.CSRD2.ReferenceDataSubscriptionReceiverCBS/ReceiveReferenceData
      |    </wsa:Action>
      |        <wsa:MessageID xmlns:wsa="http://www.w3.org/2005/08/addressing">
      |      uuid:{{$randomUUID}}
      |    </wsa:MessageID>
      |  </soap:Header>
      |  <soap:Body>
      |    <csrd:ReceiveReferenceDataReqMsg>
      |      <msg:ReceiveReferenceDataRequestType>
      |        <msg:MessageHeader>
      |          <hdr:MessageID>MSG-2024-12-30-003</hdr:MessageID>
      |          <hdr:MessageTimestamp>2024-12-30T12:00:00Z</hdr:MessageTimestamp>
      |          <hdr:SenderID>CUSTOMS_AUTHORITY_FR</hdr:SenderID>
      |          <hdr:ReceiverID>SUBSCRIBER_SYSTEM_03</hdr:ReceiverID>
      |        </msg:MessageHeader>
      |        <msg:ErrorReport>eSBmb3JtYXQu</msg:ErrorReport>
      |      </msg:ReceiveReferenceDataRequestType>
      |    </csrd:ReceiveReferenceDataReqMsg>
      |  </soap:Body>
      |</soap:Envelope>""".stripMargin

  private val invalidSubscriptionMessage: String =
    """<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
      |               xmlns:csrd="http://xmlns.ec.eu/CallbackService/CSRD2/IReferenceDataSubscriptionReceiverCBS/V4"
      |               xmlns:msg="http://xmlns.ec.eu/BusinessObjects/CSRD2/ReferenceDataSubscriptionReceiverCBSServiceType/V4"
      |               xmlns:hdr="http://xmlns.ec.eu/BusinessObjects/CSRD2/MessageHeaderType/V2"
      |               xmlns:rdlist="http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntityEntryListType/V3"
      |               xmlns:rdentity="http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntityType/V3"
      |               xmlns:rdentry="http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntryType/V3"
      |               xmlns:rdstatus="http://xmlns.ec.eu/BusinessObjects/CSRD2/RDStatusType/V3"
      |               xmlns:lsd="http://xmlns.ec.eu/BusinessObjects/CSRD2/LsdListType/V2">
      |  <soap:Header>
      |    <wsa:Action xmlns:wsa="http://www.w3.org/2005/08/addressing">
      |      CCN2.Service.Customs.Default.CSRD2.ReferenceDataSubscriptionReceiverCBS/ReceiveReferenceData
      |    </wsa:Action>
      |  </soap:Header>
      |  <soap:Body>
      |    <csrd:ReceiveReferenceDataReqMsg>
      |      <msg:ReceiveReferenceDataRequestType>
      |        <msg:MessageHeader>
      |          <hdr:MessageID>MSG-2024-12-30-001</hdr:MessageID>
      |          <hdr:MessageTimestamp>2024-12-30T10:30:00Z</hdr:MessageTimestamp>
      |          <hdr:SenderID>CUSTOMS_AUTHORITY_UK</hdr:SenderID>
      |          <hdr:ReceiverID>SUBSCRIBER_SYSTEM_01</hdr:ReceiverID>
      |        </msg:MessageHeader>
      |      </msg:ReceiveReferenceDataRequestType>
      |    </csrd:ReceiveReferenceDataReqMsg>
      |  </soap:Body>
      |</soap:Envelope>""".stripMargin

  override def beforeEach(): Unit = {
    await(mongoDatabase.drop().toFuture())
    await(messageWrapperRepository.ensureIndexes())
    await(workItemRepository.ensureIndexes())
  }

  override def afterAll(): Unit = {
    await(mongoDatabase.drop().toFuture())
  }

  "POST / endpoint" should {
    "return Accepted with a valid ReferenceDataExport request" in {
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
          .post(InboundSoapMessage.valid_soap_message.toString)
          .futureValue

      response.status shouldBe ACCEPTED
    }

    "return OK with a valid IsAlive request" in {
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
          .post(InboundSoapMessage.valid_soap_is_alive_message.toString)
          .futureValue

      response.status shouldBe OK
      response.body[Elem] shouldBe OutboundSoapMessage.valid_is_alive_response_message
    }

    "return Accepted with a valid Error Report request" in {
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
          .post(InboundSoapMessage.valid_soap_error_report_message.toString)
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
          .post(InboundSoapMessage.invalid_soap_message.toString)
          .futureValue

      response.status shouldBe BAD_REQUEST
    }

    "return bad request if the request does not contains a valid soap" in {
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
          .post(InboundSoapMessage.invalid_soap_message.toString)
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
          .post(InboundSoapMessage.valid_soap_message.toString)
          .futureValue

      val responseDuplicate =
        wsClient
          .url(url)
          .addHttpHeaders(
            "x-files-included" -> "true",
            "Content-Type" -> "application/xml"
          )
          .post(InboundSoapMessage.valid_soap_message.toString)
          .futureValue

      response.status shouldBe ACCEPTED
      responseDuplicate.status shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "POST / endpoint for ReferenceDataSubscription" should {
    "return OK and queue work item for subscription message with RDEntityList" in {
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
          .addHttpHeaders("Content-Type" -> "application/xml")
          .post(subscriptionMessageWithRDEntityList)
          .futureValue

      response.status shouldBe OK
      response.body.toString should include("12345678-1234-1234-1234-123456789012")
      response.body.toString should include("successfully queued")
      
      val workItems = await(workItemRepository.collection.find().toFuture())
      workItems.size shouldBe 1
      workItems.head.status shouldBe ProcessingStatus.ToDo
      workItems.head.item.correlationID shouldBe "12345678-1234-1234-1234-123456789012"
    }

    "return Accepted for subscription message with ErrorReport" in {
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
          .addHttpHeaders("Content-Type" -> "application/xml")
          .post(subscriptionMessageWithErrorReport)
          .futureValue

      response.status shouldBe BAD_REQUEST
      response.body.toString should include("Error message is not yet implemented")

    }

    "return Bad Request for subscription message without RDEntityList or ErrorReport or messageId" in {
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
          .addHttpHeaders("Content-Type" -> "application/xml")
          .post(invalidSubscriptionMessage)
          .futureValue

      response.status shouldBe BAD_REQUEST
    }
  }
