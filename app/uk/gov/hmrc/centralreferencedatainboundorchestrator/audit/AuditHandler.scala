/*
 * Copyright 2023 HM Revenue & Customs
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

import com.google.inject.Inject
import play.api.libs.json.{JsObject, JsString}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.MessageWrapper
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions
import uk.gov.hmrc.play.audit.http.connector.AuditResult.{Disabled, Failure}
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import scala.concurrent.{ExecutionContext, Future}

class AuditHandler @Inject() (auditConnector: AuditConnector, appConfig: AppConfig)(implicit ec: ExecutionContext) {

  def auditNewMessageWrapper(payload: String)
                            (implicit hc: HeaderCarrier): Future[AuditResult] = {
    
   val detailJsObject = JsObject(
     Seq(
       "messageWrapper"     -> JsString(payload)
     )
   )
      
    val extendedDataEvent = ExtendedDataEvent(
      auditSource = appConfig.appName,
      auditType = "InboundMessageReceived",
      detail = detailJsObject,
      tags = AuditExtensions.auditHeaderCarrier(hc).toAuditTags("Inbound message received", "/")
    )

    auditConnector.sendExtendedEvent(extendedDataEvent)
  }

  def auditAvScanning(payload: String)
                            (implicit hc: HeaderCarrier): Future[AuditResult] = {
    
    val detailJsObject = JsObject(
      Seq(
        "avScanningResult" -> JsString(payload)
      )
    )
    
    val extendedDataEvent = ExtendedDataEvent(
      auditSource = appConfig.appName,
      auditType = "AvScanningResultReceived",
      detail = detailJsObject,
      tags = AuditExtensions.auditHeaderCarrier(hc).toAuditTags("AV Scanning Result Received", "/")
    )

    auditConnector.sendExtendedEvent(extendedDataEvent)
  }
  
}
