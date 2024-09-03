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

import play.api.mvc.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.services.SdesService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

@Singleton
class SdesCallbackController @Inject()(sdesService: SdesService, cc: ControllerComponents)(using executionContext: ExecutionContext)
  extends BackendController(cc):
  
  def sdesCallback: Action[SdesCallbackResponse] = Action.async(parse.json[SdesCallbackResponse]) { implicit request =>
    sdesService.processCallback(request.body) transform {
      case Success(_) => Success(Accepted)
      case Failure(err: Throwable) => err match
        case NoMatchingUIDInMongoError(_) => Success(NotFound)
        case InvalidSDESNotificationError(_) => Success(BadRequest)
        case MongoReadError(_) | MongoWriteError(_) => Success(InternalServerError)
        case _ => Success(InternalServerError)
    }
  }
