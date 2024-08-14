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

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.HttpClientV2Support

import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.Elem

class EisConnectorSpec
  extends AnyWordSpec
  , Matchers
  , HttpClientV2Support
  , ScalaFutures
  , IntegrationPatience {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private lazy val app: Application =
    GuiceApplicationBuilder()
      .configure(
        "microservice.services.eis-api.host" -> "localhost",
        "microservice.services.eis-api.port" -> "7251",
        "microservice.services.eis-api.path" -> "/central-reference-data-eis-stub"
      ).build()

  private lazy val connector = app.injector.instanceOf[EisConnector]

  private val testBody: Elem =
    <MainMessage>
      <Body>
        <TaskIdentifier>780912</TaskIdentifier>
        <AttributeName>ReferenceData</AttributeName>
        <MessageType>gZip</MessageType>
        <IncludedBinaryObject>c04a1612-705d-4373-8840-9d137b14b30a</IncludedBinaryObject>
        <MessageSender>CS/RD2</MessageSender>
      </Body>
    </MainMessage>

  "eis returns ACCEPTED" should {
    "return true" in {
      whenReady(connector.forwardMessage(testBody)) { res =>
        res shouldBe ((): Unit)
      }
    }
  }


}
