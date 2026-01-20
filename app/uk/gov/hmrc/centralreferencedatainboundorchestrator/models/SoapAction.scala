/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.libs.json.{Format, JsonValidationError, Reads, Writes}

enum SoapAction(private val action: String) {
  case ReferenceDataExport extends SoapAction(SoapAction.ReferenceDataExportAction)
  case ReferenceDataSubscription extends SoapAction(SoapAction.ReferenceDataSubscriptionAction)
  case IsAlive extends SoapAction(SoapAction.IsAliveAction)
}

object SoapAction {
  private lazy val ReferenceDataExportAction         =
    "CCN2.Service.Customs.Default.CSRD.ReferenceDataExportReceiverCBS/ReceiveReferenceData"
  private lazy val ReferenceDataSubscriptionAction   =
    "CCN2.Service.Customs.Default.CSRD.ReferenceDataSubscriptionReceiverCBS/ReceiveReferenceData"
  private lazy val IsAliveAction                     = "CCN2.Service.Customs.Default.CSRD.ReferenceDataExportReceiverCBS/IsAlive"
  def fromString(action: String): Option[SoapAction] =
    SoapAction.values.find(_.action == action)

  private[models] val actions = values.map(_.action)

  given Format[SoapAction] = Format(
    Reads
      .of[String]
      .filter(JsonValidationError("Unknown soap action"))(actions.contains)
      .map(value => SoapAction.fromString(value).get),
    Writes.of[String].contramap(_.action)
  )
}
