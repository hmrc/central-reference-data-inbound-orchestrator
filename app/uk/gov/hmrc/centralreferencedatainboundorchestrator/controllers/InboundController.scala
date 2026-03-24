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
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.EISWorkItemRepository
import uk.gov.hmrc.centralreferencedatainboundorchestrator.services.{InboundControllerService, ValidationService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import java.util.UUID
import scala.util.Try

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.xml.NodeSeq

@Singleton
class InboundController @Inject() (
  cc: ControllerComponents,
  inboundControllerService: InboundControllerService,
  validationService: ValidationService,
  auditHandler: AuditHandler,
  workItemRepo: EISWorkItemRepository,
  appConfig: AppConfig
)(using ec: ExecutionContext)
    extends BackendController(cc)
    with Logging:

  private val FileIncludedHeader = "x-files-included"

  def submit(): Action[String] = Action.async(parse.tolerantText) { implicit request =>
    auditHandler.auditNewMessageWrapper(request.body)

    val result = validationService.validateAndExtractAction(request.body) match {
      case Some((soapAction, validatedMessage)) =>
        if appConfig.logIncomingMessages /*&& soapAction != SoapAction.ReferenceDataExport*/ then
          logger.warn(s"Incoming SOAP message with action $soapAction: ${request.body}")
        Some(handleInboundMessage(soapAction, validatedMessage))
      case None                                 =>
        if appConfig.logIncomingMessages then
          logger.warn(s"Incoming SOAP message with unrecognised action: ${request.body}")
        None
    }

    result.getOrElse(Future.successful(BadRequest))
  }

  private def handleInboundMessage(action: SoapAction, validatedMessage: NodeSeq)(using
    request: Request[?]
  ): Future[Result] =
    action match {
      case SoapAction.ReferenceDataExport       =>
        handleReferenceDataMessage(validatedMessage)
      case SoapAction.ReferenceDataSubscription =>
        handleReferenceDataSubscription(validatedMessage)
      case SoapAction.IsAliveExport             =>
        Future.successful(Ok(IsAliveResponse.exportXML))
      case SoapAction.IsAliveSubscription       =>
        Future.successful(Ok(IsAliveResponse.subscriptionXML))
    }

  private def handleReferenceDataMessage(validatedMessage: NodeSeq)(using request: Request[?]): Future[Status] = {
    if !getHasFilesHeader.getOrElse(false) then {
      logger.warn(s"Reference Data Export without required '$FileIncludedHeader' header provided")
    }
    val result = for
      hasFilesHeader <- getHasFilesHeader
      if hasFilesHeader
      innerMessage   <- validationService.extractInnerMessage(validatedMessage)
    yield processInboundMessage(innerMessage, SoapAction.ReferenceDataExport)

    result.getOrElse(Future.successful(BadRequest))
  }

  private def handleReferenceDataSubscription(validatedMessage: NodeSeq): Future[Result] = {
    val hasRDEntityList = (validatedMessage \\ "ReceiveReferenceDataRequestType" \ "RDEntityList").nonEmpty
    val hasErrorReport  = (validatedMessage \\ "ReceiveReferenceDataRequestType" \ "ErrorReport").nonEmpty

    (hasRDEntityList, hasErrorReport) match {
      case (true, _)     =>
        extractUuid(validatedMessage) match {
          case Some(uuid) =>
            workItemRepo
              .set(EISRequest(validatedMessage.toString, uuid, SoapAction.ReferenceDataSubscription))
              .map(_ => Accepted(s"Message with UID: $uuid, successfully queued"))
          case None       =>
            Future.successful(BadRequest("Missing or invalid UUID in MessageID"))
        }
      case (false, true) =>
        processInboundMessage(validatedMessage, SoapAction.ReferenceDataSubscription)
      case _             =>
        Future.successful(BadRequest("Payload must contain either RDEntityList or ErrorReport"))
    }
  }

  private def extractUuid(soapMessage: NodeSeq): Option[String] =
    (soapMessage \\ "Header" \ "MessageID").headOption
      .map(_.text.trim.stripPrefix("uuid:"))
      .filter(_.nonEmpty)
      .flatMap(uuid => Try(UUID.fromString(uuid)).toOption.map(_.toString))

  private def processInboundMessage(body: NodeSeq, action: SoapAction): Future[Status] =
    inboundControllerService.processMessage(body, action).transform {
      case Success(_)              =>
        Success(Accepted)
      case Failure(err: Throwable) =>
        err match {
          case InvalidXMLContentError(_)              => Success(BadRequest)
          case MongoReadError(_) | MongoWriteError(_) => Success(InternalServerError)
          case _                                      => Success(InternalServerError)
        }
    }

  private def getHasFilesHeader(implicit request: Request[?]): Option[Boolean] =
    request.headers.get(FileIncludedHeader).flatMap(_.toBooleanOption)
