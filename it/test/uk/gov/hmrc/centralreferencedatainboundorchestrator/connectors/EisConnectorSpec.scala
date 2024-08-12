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
