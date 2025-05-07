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

    val result = for
      validatedMessage <- validationService.validateSoapMessage(request.body)
      soapAction       <- validationService.extractSoapAction(validatedMessage)
    yield handleInboundMessage(soapAction, validatedMessage)

    result.getOrElse(Future.successful(BadRequest))
  }

  private def handleInboundMessage(action: SoapAction, validatedMessage: NodeSeq)(using
    request: Request[?]
  ): Future[Result] =
    action match {
      case SoapAction.ReceiveReferenceData =>
        handleReferenceDataMessage(validatedMessage)
      case SoapAction.IsAlive              =>
        Future.successful(Ok(IsAliveResponse.xml))
    }

  private def handleReferenceDataMessage(validatedMessage: NodeSeq)(using request: Request[?]): Future[Status] = {
    val result = for
      hasFilesHeader <- getHasFilesHeader
      if hasFilesHeader
      innerMessage   <- validationService.extractInnerMessage(validatedMessage)
    yield processInboundMessage(innerMessage)

    result.getOrElse(Future.successful(BadRequest))
  }

  private def processInboundMessage(body: NodeSeq): Future[Status] =
    inboundControllerService.processMessage(body).transform {
      case Success(_)              =>
        Success(Accepted)
      case Failure(err: Throwable) =>
        err match {
          case InvalidXMLContentError(_)                    => Success(BadRequest)
          case MongoReadError(_, _) | MongoWriteError(_, _) => Success(InternalServerError)
          case _                                            => Success(InternalServerError)
        }
    }

  private def getHasFilesHeader(implicit request: Request[?]): Option[Boolean] =
    request.headers.get(FileIncludedHeader).flatMap(_.toBooleanOption)
