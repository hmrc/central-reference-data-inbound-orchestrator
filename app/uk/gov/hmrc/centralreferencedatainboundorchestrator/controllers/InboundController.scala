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
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.audit.AuditHandler
import uk.gov.hmrc.centralreferencedatainboundorchestrator.services.InboundControllerService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.io.StringReader
import javax.inject.{Inject, Singleton}
import javax.xml.XMLConstants
import javax.xml.transform.stream.StreamSource
import javax.xml.validation.SchemaFactory
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import scala.xml.NodeSeq

@Singleton
class InboundController @Inject()(
                                   cc: ControllerComponents,
                                   inboundControllerService: InboundControllerService,
                                   auditHandler: AuditHandler)
                                 (using ec: ExecutionContext)
  extends BackendController(cc) with Logging:

  private val FileIncludedHeader = "x-files-included"

  def submit(): Action[NodeSeq] = Action.async(parse.xml) { implicit request =>
    auditHandler.auditNewMessageWrapper(request.body.toString)
    if hasFilesHeader && validateRequestBody(request.body) then
      inboundControllerService.processMessage(request.body) transform {
        case Success(_) => Success(Accepted)
        case Failure(err: Throwable) => err match
          case InvalidXMLContentError(_) => Success(BadRequest)
          case MongoReadError(_) | MongoWriteError(_) => Success(InternalServerError)
          case _ => Success(InternalServerError)
      }
    else
      Future.successful(BadRequest)
  }

  private def hasFilesHeader(implicit request: Request[NodeSeq]): Boolean =
    request.headers.get(FileIncludedHeader).exists(_.toBooleanOption.getOrElse(false))

  private def validateRequestBody(body: NodeSeq) =
    Try {
      val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
      val xsd = getClass.getResourceAsStream("/schemas/csrd120main-v1.xsd") // this is temporary until we get the correct XSD file from public-soap-proxy
      val schema = factory.newSchema(new StreamSource(xsd))
      val validator = schema.newValidator()
      validator.validate(new StreamSource(new StringReader(body.toString)))
    } match {
      case Success(_) => true
      case _ => false
    }
