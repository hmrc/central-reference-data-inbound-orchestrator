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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import play.api.libs.json.{JsObject, JsString}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.http.connector.AuditResult.*
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuditHandlerSpec extends AnyWordSpec, Matchers, ScalaFutures:
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockAppConfig: AppConfig = mock[AppConfig]

  val appName = "TestApp"

  when(mockAppConfig.appName).thenReturn(appName)

  val handler: AuditHandler = AuditHandler(mockAuditConnector, mockAppConfig)

  val testCorrelationId = "CORRELATION_ID"
  val testPayload = "TEST PAYLOAD"

  val successfulAudit: Future[AuditResult] = Future.successful(Success)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "The audit handler" should {
    "send a new message wrapper received event" in {
      val expectedEvent = ExtendedDataEvent(
        auditSource = appName,
        auditType = "InboundMessageReceived",
        detail = JsObject(
          Seq(
            "payload"     -> JsString(testPayload)
          )
        )
      )

      when(mockAuditConnector.sendExtendedEvent(any)(any, any))
        .thenReturn(successfulAudit)

      val result = handler.auditNewMessageWrapper(testPayload).futureValue

      result shouldBe Success

      verify(mockAuditConnector, times(1)).sendExtendedEvent(any)(any, any)
    }
  }
