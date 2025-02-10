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

import com.github.tomakehurst.wiremock.client.WireMock.*
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.*
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.test.ExternalWireMockSupport

import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.Elem

class EisConnectorSpec
    extends AnyWordSpec,
      ExternalWireMockSupport,
      Matchers,
      ScalaFutures,
      IntegrationPatience,
      GuiceOneAppPerSuite,
      MockitoSugar,
      BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val appConfig: AppConfig       = mock[AppConfig]
  private val httpClientV2: HttpClientV2 = app.injector.instanceOf[HttpClientV2]

  when(appConfig.eisUrl).thenReturn(s"http://$externalWireMockHost:$externalWireMockPort")
  when(appConfig.eisPath).thenReturn("/csrd/referencedataupdate/v1")
  when(appConfig.eisBearerToken).thenReturn("test")

  private val path = "/csrd/referencedataupdate/v1"

  private val connector = new EisConnector(httpClientV2, appConfig)

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

  "EIS Connector" should {
    "return successfully if the service is running" in {

      stubFor(
        post(urlEqualTo(path))
          .withRequestBody(equalToXml(testBody.toString))
          .willReturn(
            aResponse().withStatus(ACCEPTED)
          )
      )

      val result = await(connector.forwardMessage(testBody))

      result shouldBe true
    }

    "eis returns BAD REQUEST" in {
      stubFor(
        post(urlEqualTo(path))
          .withRequestBody(equalToXml(testBody.toString))
          .willReturn(
            aResponse().withStatus(BAD_REQUEST)
          )
      )

      val result = await(connector.forwardMessage(testBody))

      result shouldBe false
    }

    "eis returns INTERNAL SERVER ERROR" in {
      stubFor(
        post(urlEqualTo(path))
          .withRequestBody(equalToXml(testBody.toString))
          .willReturn(
            aResponse().withStatus(INTERNAL_SERVER_ERROR)
          )
      )

      val result = await(connector.forwardMessage(testBody))

      result shouldBe false
    }
  }
}
