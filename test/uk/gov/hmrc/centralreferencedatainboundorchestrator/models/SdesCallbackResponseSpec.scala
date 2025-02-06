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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, JsSuccess, Json}
import java.time.LocalDateTime

class SdesCallbackResponseSpec extends AnyWordSpec, Matchers {

  private val testResponse = SdesCallbackResponse(
    "notification",
    "filename",
    "correlationID",
    LocalDateTime.of(2024, 9, 9, 15, 30, 0, 0),
    Some("CheckSumAlgorithm"),
    Some("checksum"),
    Some(LocalDateTime.of(2024, 9, 10, 14, 30, 0, 0)),
    List(
      Property("Property1", "1"),
      Property("Property2", "2"),
      Property("Property3", "3")
    ),
    Some("Some Failure")
  )

  private val testJson: JsObject = Json.obj(
    "notification"      -> "notification",
    "filename"          -> "filename",
    "correlationID"     -> "correlationID",
    "dateTime"          -> LocalDateTime.of(2024, 9, 9, 15, 30, 0, 0),
    "checksumAlgorithm" -> Some("CheckSumAlgorithm"),
    "checksum"          -> Some("checksum"),
    "availableUntil"    -> Some(LocalDateTime.of(2024, 9, 10, 14, 30, 0, 0)),
    "properties"        -> List(
      Property("Property1", "1"),
      Property("Property2", "2"),
      Property("Property3", "3")
    ),
    "failureReason"     -> Some("Some Failure")
  )

  "SDES Response" should {
    "Serialise properly" in {
      val actual = Json.toJson(testResponse)

      actual shouldBe testJson
    }

    "Deserialize properly" in {
      val actual = testJson.validate[SdesCallbackResponse]
      actual shouldBe JsSuccess(testResponse)
    }
  }
}
