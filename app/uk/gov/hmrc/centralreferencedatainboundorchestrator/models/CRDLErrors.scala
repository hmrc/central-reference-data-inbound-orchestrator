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

abstract class CRDLErrors(message: String) extends Throwable(message)
case class InvalidXMLContentError(message: String) extends CRDLErrors(message)

case class MongoWriteError(message: String) extends CRDLErrors(message)

case class MongoReadError(message: String) extends CRDLErrors(message)

case class EisResponseError(message: String) extends CRDLErrors(message)

case class AVScanFailureError(message: String) extends CRDLErrors(message)

case class NoMatchingUIDInMongoError(message: String) extends CRDLErrors(message)

case class InvalidSDESNotificationError(message: String) extends CRDLErrors(message)
