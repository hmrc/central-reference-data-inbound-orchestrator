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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.audit

import org.mockito.ArgumentCaptor
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.MessageStatus.Received
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.SoapAction.ReferenceDataExport
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.{MessageWrapper, SdesCallbackResponse}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.http.connector.AuditResult.*
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuditHandlerSpec extends AnyWordSpec, Matchers, BeforeAndAfterEach, ScalaFutures:
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockAppConfig: AppConfig           = mock[AppConfig]

  val appName = "TestApp"

  when(mockAppConfig.appName).thenReturn(appName)

  val handler: AuditHandler = AuditHandler(mockAuditConnector, mockAppConfig)

  val testCorrelationId                          = "CORRELATION_ID"
  val testPayload                                = "TEST PAYLOAD"
  val expectedMessageReceived: JsObject          = Json.obj(
    "messageWrapper" -> testPayload
  )
  val testCallbackResponse: SdesCallbackResponse = SdesCallbackResponse(
    "Notification",
    "FileName",
    "CorrelationId",
    LocalDateTime.of(2024, 10, 30, 9, 0, 0),
    Some("checksumAlgorithm"),
    Some("checksum"),
    None,
    List(),
    None
  )
  val expectedFileProcessedDetails: JsObject     = Json.obj(
    "referenceDataFileProcessed" -> Json.obj(
      "notification"      -> "Notification",
      "filename"          -> "FileName",
      "correlationID"     -> "CorrelationId",
      "dateTime"          -> "2024-10-30T09:00:00",
      "checksumAlgorithm" -> "checksumAlgorithm",
      "checksum"          -> "checksum",
      "properties"        -> Json.arr()
    )
  )
  val testMessageWrapper: MessageWrapper         =
    MessageWrapper(UUID.randomUUID().toString, "PAYLOAD", Received, ReferenceDataExport)

  val successfulAudit: Future[AuditResult] = Future.successful(Success)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuditConnector)

  }

  "The audit handler" should {
    "send a new message wrapper received event" in {

      val captor: ArgumentCaptor[ExtendedDataEvent] = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])

      when(mockAuditConnector.sendExtendedEvent(captor.capture())(any, any))
        .thenReturn(successfulAudit)

      val result = handler.auditNewMessageWrapper(testPayload).futureValue

      result shouldBe Success

      val sentEvent = captor.getValue

      sentEvent.auditSource                 shouldBe appName
      sentEvent.auditType                   shouldBe "InboundMessageReceived"
      sentEvent.tags.get("transactionName") shouldBe Some("Inbound message received")
      sentEvent.tags.get("path")            shouldBe Some("/central-reference-data-inbound-orchestrator")
      sentEvent.detail                      shouldBe expectedMessageReceived

      verify(mockAuditConnector, times(1)).sendExtendedEvent(any)(any, any)
    }

    "send a new message wrapper and payload received event" in {

      val captor: ArgumentCaptor[ExtendedDataEvent] = ArgumentCaptor.forClass(classOf[ExtendedDataEvent])

      when(mockAuditConnector.sendExtendedEvent(captor.capture())(any, any))
        .thenReturn(successfulAudit)

      val result = handler.auditFileProcessed(testCallbackResponse).futureValue

      result shouldBe Success

      val sentEvent = captor.getValue

      sentEvent.auditSource                 shouldBe appName
      sentEvent.auditType                   shouldBe "ReferenceDataFileProcessed"
      sentEvent.tags.get("transactionName") shouldBe Some("Reference Data File Processed")
      sentEvent.tags.get("path")            shouldBe Some("/central-reference-data-inbound-orchestrator/services/crdl/callback")
      sentEvent.detail                      shouldBe expectedFileProcessedDetails

      println(s"The tags are ${sentEvent.tags}")

      verify(mockAuditConnector, times(1)).sendExtendedEvent(any)(any, any)
    }
  }
