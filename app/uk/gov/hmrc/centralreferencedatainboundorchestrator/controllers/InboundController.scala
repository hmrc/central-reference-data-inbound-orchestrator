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
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future
import scala.xml.NodeSeq

@Singleton
class InboundController @Inject()(cc: ControllerComponents)
  extends BackendController(cc):

  private val FileIncludedHeader = "x-files-included"

  def submit(): Action[NodeSeq] = Action.async(parse.xml) { implicit request =>
    if request.headers.get(FileIncludedHeader).contains("true") then
      //TODO: Store the message into mongo, this will be done as part of CRDL-73.s
      Future.successful(Accepted)
    else
      Future.successful(BadRequest)
  }
