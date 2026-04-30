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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.controllers

import play.api.Logging
import play.api.mvc.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.audit.AuditHandler
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.services.SdesService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

@Singleton
class SdesCallbackController @Inject() (sdesService: SdesService, cc: ControllerComponents, auditHandler: AuditHandler)(
  using executionContext: ExecutionContext
) extends BackendController(cc)
    with Logging:

  def sdesCallback: Action[SdesCallbackResponse] = Action.async(parse.json[SdesCallbackResponse]) { implicit request =>
    auditHandler.auditFileProcessed(request.body)
    sdesService.processCallback(request.body) transform {
      case Success(_)              => Success(Accepted)
      case Failure(err: Throwable) =>
        err match
          case e: NoMatchingUIDInMongoError    =>
            logger.error(
              s"No matching UID in Mongo for SDES callback with correlationId '${request.body.correlationID}': ${e.getMessage}",
              e
            )
            Success(NotFound)
          case e: InvalidSDESNotificationError =>
            logger.warn(s"Invalid SDES notification for correlationId '${request.body.correlationID}': ${e.getMessage}")
            Success(BadRequest)
          case e: MongoReadError               =>
            logger.error(
              s"Mongo read error processing SDES callback for correlationId '${request.body.correlationID}': ${e.getMessage}",
              e
            )
            Success(InternalServerError)
          case e: MongoWriteError              =>
            logger.error(
              s"Mongo write error processing SDES callback for correlationId '${request.body.correlationID}': ${e.getMessage}",
              e
            )
            Success(InternalServerError)
          case e                               =>
            logger.error(
              s"Unexpected error processing SDES callback for correlationId '${request.body.correlationID}': ${e.getMessage}",
              e
            )
            Success(InternalServerError)
    }
  }
