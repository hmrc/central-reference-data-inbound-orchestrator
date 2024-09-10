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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.reporting

import play.api.libs.json.{JsNumber, JsObject, JsString}

case class AuditEvent(auditType: String, transactionName: String, detail: JsObject)

object AuditEvent {
  def receivedEvent(
    messageId: String,
    payload: String
  ): AuditEvent =
    AuditEvent(
      "InboundMessageReceived",
      "Inbound message received",
      JsObject(
        Seq(
          "messageId"   -> JsString(messageId),
          "payload"     -> JsString(payload)
        )
      )
    )
}
