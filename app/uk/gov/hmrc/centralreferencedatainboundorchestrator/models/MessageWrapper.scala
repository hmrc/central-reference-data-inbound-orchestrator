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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.models

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.{Format, Json, OFormat, OWrites, Reads, __}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.MessageStatus.MessageStatus
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.SoapAction.ReceiveReferenceData
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

case class MessageWrapper(
  uid: String,
  payload: String,
  status: MessageStatus,
  messageType: SoapAction = ReceiveReferenceData,
  lastUpdated: Instant,
  receivedTimestamp: Instant
)

object MessageWrapper:
  def apply(uid: String, payload: String, status: MessageStatus, messageType: SoapAction): MessageWrapper =
    MessageWrapper(uid, payload, status, messageType, Instant.now, Instant.now)

  given dateFormat: Format[Instant] = MongoJavatimeFormats.instantFormat

  // 👇 custom Reads that defaults messageType when missing
  private val mongoReads: Reads[MessageWrapper] =
    (
      (__ \ "uid").read[String] and
        (__ \ "payload").read[String] and
        (__ \ "status").read[MessageStatus] and
        (__ \ "lastUpdated").read[Instant](dateFormat) and
        (__ \ "receivedTimestamp").read[Instant](dateFormat) and
        (__ \ "messageType").readNullable[SoapAction].map(_.getOrElse(ReceiveReferenceData))
    ) { (uid, payload, status, lastUpdated, receivedTimestamp, messageType) =>
      MessageWrapper(uid, payload, status, messageType, lastUpdated, receivedTimestamp)
    }

  private val mongoWrites: OWrites[MessageWrapper] = Json.writes[MessageWrapper]

  given mongoFormat: OFormat[MessageWrapper] = OFormat(mongoReads, mongoWrites)
