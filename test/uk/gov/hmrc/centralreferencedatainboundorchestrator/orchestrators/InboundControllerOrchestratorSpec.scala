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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.orchestrators

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.RecoverMethods.recoverToExceptionIf
import org.scalatestplus.mockito.MockitoSugar.mock
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.test.Helpers.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.MessageWrapperRepository
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.MessageStatus
import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.concurrent.ScalaFutures
import scala.concurrent.Future

class InboundControllerOrchestratorSpec extends AnyWordSpec, Matchers, ScalaFutures:

  lazy val mockMessageWrapperRepository: MessageWrapperRepository = mock[MessageWrapperRepository]
  private val orchestrator = new InboundControllerOrchestrator(mockMessageWrapperRepository)

  // This is the expected body we need to send to EIS, using this for test purposes
  // until we get a real sample input file.
  private val validTestBody = <MainMessage>
    <Body>
      <TaskIdentifier>780912</TaskIdentifier>
      <AttributeName>ReferenceData</AttributeName>
      <MessageType>gZip</MessageType>
      <IncludedBinaryObject>c04a1612-705d-4373-8840-9d137b14b30a</IncludedBinaryObject>
      <MessageSender>CS/RD2</MessageSender>
    </Body>
  </MainMessage>

  private val invalidTestBody = <MainMessage>
    <Body>
      <TaskIdentifier>780912</TaskIdentifier>
      <AttributeName>ReferenceData</AttributeName>
      <MessageType>gZip</MessageType>
      <IncludedBinaryObject> </IncludedBinaryObject>
      <MessageSender>CS/RD2</MessageSender>
    </Body>
  </MainMessage>

  "processMessage" should {
    "return true when retrieving UID from XML and storing xml message in Mongo successfully" in {
      when(mockMessageWrapperRepository.insertMessageWrapper(any(), any(), any())(using any()))
        .thenReturn(Future.successful(true))

      val result = orchestrator.processMessage(validTestBody).futureValue

      result shouldBe true
    }

    "return false when failing to store message in Mongo" in {
      when(mockMessageWrapperRepository.insertMessageWrapper(any(), any(), any())(using any()))
        .thenReturn(Future.successful(false))

      val result = orchestrator.processMessage(validTestBody).futureValue

      result shouldBe false
    }

    "Throw an exception if UID is missing in XML" in {
      when(mockMessageWrapperRepository.insertMessageWrapper(any(), any(), any())(using any()))
        .thenReturn(Future.successful(false))

      val result = orchestrator.processMessage(invalidTestBody)

      recoverToExceptionIf[Throwable](result).map { rt =>
        rt.getMessage shouldBe "Failed for unknown reason, potentially an empty UID or a missing Node."
      }
    }
    
  }