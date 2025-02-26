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
import play.api.Logging
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.ws.XMLBodyWritables.writeableOf_NodeSeq
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps, UpstreamErrorResponse}

import java.time.format.DateTimeFormatter
import java.time.{Clock, ZoneOffset}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.xml.NodeSeq

class EisConnector @Inject() (httpClient: HttpClientV2, appConfig: AppConfig, clock: Clock) extends Logging:

  private val httpDateFormatter = DateTimeFormatter.RFC_1123_DATE_TIME
  def forwardMessage(body: NodeSeq)(using
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Boolean] =
    val url           = s"${appConfig.eisUrl}${appConfig.eisPath}"
    val now           = clock.instant().atZone(ZoneOffset.UTC)
    val correlationId = UUID.randomUUID().toString
    httpClient
      .post(url"$url")
      .setHeader(HeaderNames.ACCEPT -> MimeTypes.XML)
      .setHeader(HeaderNames.CONTENT_TYPE -> MimeTypes.XML)
      .setHeader(HeaderNames.AUTHORIZATION -> s"Bearer ${appConfig.eisBearerToken}")
      .setHeader(HeaderNames.X_FORWARDED_HOST -> "central-reference-data-inbound-orchestrator")
      .setHeader("X-Correlation-Id" -> correlationId)
      .setHeader(HeaderNames.DATE -> httpDateFormatter.format(now))
      .withBody(body)
      .execute[Either[UpstreamErrorResponse, Unit]]
      .transformWith {
        case Success(Right(_))  =>
          Future.successful(true)
        case Success(Left(err)) =>
          logger.error(s"Error while calling EIS with correlation ID : $correlationId", err)
          Future.successful(false)
        case Failure(err)       =>
          logger.error(s"Error while calling EIS with correlation ID : $correlationId", err)
          Future.successful(false)
      }
