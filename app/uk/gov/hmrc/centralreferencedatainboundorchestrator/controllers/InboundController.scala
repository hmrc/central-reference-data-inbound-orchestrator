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
import uk.gov.hmrc.centralreferencedatainboundorchestrator.services.{InboundControllerService, ValidationService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.xml.NodeSeq

@Singleton
class InboundController @Inject() (
  cc: ControllerComponents,
  inboundControllerService: InboundControllerService,
  validationService: ValidationService,
  auditHandler: AuditHandler
)(using ec: ExecutionContext)
    extends BackendController(cc)
    with Logging:

  private val FileIncludedHeader = "x-files-included"

  def submit(): Action[String] = Action.async(parse.tolerantText) { implicit request =>
    auditHandler.auditNewMessageWrapper(request.body)
    if hasFilesHeader then
      validationService.validateFullSoapMessage(request.body) match {
        case Some(body) =>
          determineResult(body)
        case None       =>
          Future.successful(BadRequest)
      }
    else Future.successful(BadRequest)
  }

  private def determineResult(body: NodeSeq): Future[Status] =
    inboundControllerService.processMessage(body) transform {
      case Success(_)              =>
        Success(Accepted)
      case Failure(err: Throwable) =>
        err match {
          case InvalidXMLContentError(_)              => Success(BadRequest)
          case MongoReadError(_) | MongoWriteError(_) => Success(InternalServerError)
          case _                                      => Success(InternalServerError)
        }
    }

  private def hasFilesHeader(implicit request: Request[?]): Boolean =
    request.headers.get(FileIncludedHeader).exists(_.toBooleanOption.getOrElse(false))
