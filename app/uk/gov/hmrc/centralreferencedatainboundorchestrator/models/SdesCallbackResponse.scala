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

import play.api.libs.json.{Json, OFormat, Writes}

import java.time.LocalDateTime

case class SdesCallbackResponse(
                         notification: String,
                         filename: String,
                         correlationID: String,
                         dateTime: LocalDateTime,
                         checksumAlgorithm: Option[String] = None,
                         checksum: Option[String] = None,
                         availableUntil: Option[LocalDateTime] = None,
                         properties: List[Property] = Nil,
                         failureReason: Option[String] = None
                       ) extends PropertyExtractor

object SdesCallbackResponse {
  implicit val format: OFormat[SdesCallbackResponse] = Json.format[SdesCallbackResponse]

  given Writes[SdesCallbackResponse] = Json.writes[SdesCallbackResponse]
}