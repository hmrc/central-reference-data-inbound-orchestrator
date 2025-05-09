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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.controllers.testonly

import play.api.mvc.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.{EISWorkItemRepository, MessageWrapperRepository}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class TestController @Inject() (
  messageWrapperRepository: MessageWrapperRepository,
  eisWorkItemRepository: EISWorkItemRepository,
  cc: ControllerComponents
)(using ec: ExecutionContext)
    extends BackendController(cc):

  def clearAllWrappers(): Action[AnyContent] = Action.async { implicit request =>
    messageWrapperRepository.deleteAll().map(_ => Accepted)
  }

  def clearAllWorkItems(): Action[AnyContent] = Action.async { implicit request =>
    eisWorkItemRepository.deleteAll().map(_ => Accepted)
  }

  def findById(uid: String): Action[AnyContent] = Action.async { implicit request =>
    messageWrapperRepository.findByUid(uid).map {
      case Some(messageWrapper) => Accepted(messageWrapper.status.toString)
      case None                 => NotFound
    }
  }
