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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.connectors

import com.google.inject.Inject
import play.api.http.Status.ACCEPTED
import play.api.libs.ws.XMLBodyWritables.writeableOf_NodeSeq
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits.*

import java.time.{ZoneOffset, ZonedDateTime}
import java.time.format.DateTimeFormatter
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.NodeSeq

class EisConnector @Inject() (httpClient: HttpClientV2, appConfig: AppConfig):

  def forwardMessage(body: NodeSeq)(using
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Boolean] =
    val url                   = s"${appConfig.eisUrl}${appConfig.eisPath}"
    val iso8601DateTimeFormat = DateTimeFormatter.ofPattern("EEE dd MMM yyyy HH:mm:ss 'GMT'")
    httpClient
      .post(url"$url")
      .setHeader("Accept" -> "application/xml")
      .setHeader("Content-Type" -> "application/xml;charset=UTF-8")
      .setHeader("Authorization" -> appConfig.eisBearerToken)
      .setHeader("X-Forwarded-Host" -> "central-reference-data-inbound-orchestrator")
      .setHeader("X-Correlation-Id" -> UUID.randomUUID().toString)
      .setHeader("Date" -> iso8601DateTimeFormat.format(ZonedDateTime.now(ZoneOffset.UTC)))
      .withBody(body)
      .execute[HttpResponse]
      .map(_.status == ACCEPTED)
