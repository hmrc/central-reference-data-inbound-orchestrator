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

import play.api.libs.json.*

sealed trait MessageStatus:
  val status: String

object MessageStatus:

  case object Received extends MessageStatus {
    override val status: String = "received"
  }

  case object Sent extends MessageStatus {
    override val status: String = "sent"
  }
  // Define a custom Reads instance
  given reads: Reads[MessageStatus] = Reads[MessageStatus] {
    case JsString("received") => JsSuccess(Received)
    case JsString("sent") => JsSuccess(Sent)
    case _ => JsError("Unknown message status")
  }
  // Define a custom Writes instance
  given writes: Writes[MessageStatus] = Writes[MessageStatus] { status =>
    JsString(status.status)
  }
  // Define a Format that combines both Reads and Writes
  given format: Format[MessageStatus] = Format(reads, writes)