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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.helpers

import scala.xml.Elem

object OutboundSoapMessage:
  val valid_is_alive_response_message: Elem =
    <soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope" xmlns:v1="http://xmlns.ec.eu/BusinessMessages/TATAFng/Monitoring/V1">
      <soap:Header>
        <Action xmlns="http://www.w3.org/2005/08/addressing">CCN2.Service.Customs.Default.CSRD.ReferenceDataExportReceiverCBS/IsAliveResponse</Action>
      </soap:Header>
      <soap:Body>
        <v1:isAliveRespMsg/>
      </soap:Body>
    </soap:Envelope>
