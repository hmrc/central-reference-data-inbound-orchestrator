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

import org.mockito.ArgumentMatchers.any
import org.scalatestplus.mockito.MockitoSugar
import org.scalatest.RecoverMethods.recoverToExceptionIf
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpec
import org.mockito.Mockito.{reset, when}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.test.ExternalWireMockSupport
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.xml.Elem
import scala.concurrent.Future

class EisConnectorSpec
  extends AnyWordSpec
    , ExternalWireMockSupport
    , Matchers
    , ScalaFutures
    , IntegrationPatience
    , GuiceOneAppPerSuite
    , MockitoSugar
    , BeforeAndAfterEach {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val appConfig: AppConfig             = mock[AppConfig]
  val httpClientV2: HttpClientV2       = mock[HttpClientV2]
  val requestBuilder: RequestBuilder   = mock[RequestBuilder]

  override def beforeEach(): Unit = {
    super.beforeEach()

    reset(appConfig, httpClientV2, requestBuilder)

    when(httpClientV2.post(any)(any)).thenReturn(requestBuilder)
    when(requestBuilder.withBody(any)(any, any, any)).thenReturn(requestBuilder)
    when(requestBuilder.setHeader(any)).thenReturn(requestBuilder)
  }

  val connector = new EisConnector(httpClientV2, appConfig)

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
    "return accepted" in {

      val expectedResponse = Status.ACCEPTED

      when(requestBuilder.execute[HttpResponse](any, any))
        .thenReturn(Future.successful(HttpResponse(status = expectedResponse)))

      val result = await(connector.forwardMessage(testBody))

      result.status shouldBe expectedResponse
    }
  }

  "eis returns BAD REQUEST" should {
    "return failed future with message" in {
      val expectedResponse = Status.BAD_REQUEST

      when(requestBuilder.execute[HttpResponse](any, any))
        .thenReturn(Future.successful(HttpResponse(status = expectedResponse)))

      val result = connector.forwardMessage(testBody)

      recoverToExceptionIf[Throwable](result).map { rt =>
        rt.getMessage shouldBe "Non 202 response received from EIS: HTTP 400 with body: "
      }.futureValue
    }
  }
}
