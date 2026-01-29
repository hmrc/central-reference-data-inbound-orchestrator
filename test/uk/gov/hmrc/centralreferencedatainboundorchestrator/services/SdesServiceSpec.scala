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

package uk.gov.hmrc.centralreferencedatainboundorchestrator.services

import org.apache.pekko.stream.Materializer
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.*
import org.scalactic.Prettifier.default
import org.scalatest.BeforeAndAfterEach
import org.scalatest.RecoverMethods.recoverToExceptionIf
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.centralreferencedatainboundorchestrator.audit.AuditHandler
import uk.gov.hmrc.centralreferencedatainboundorchestrator.config.AppConfig
import uk.gov.hmrc.centralreferencedatainboundorchestrator.connectors.EisConnector
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.MessageStatus.*
import uk.gov.hmrc.centralreferencedatainboundorchestrator.models.SoapAction.{ReferenceDataExport, ReferenceDataSubscription}
import uk.gov.hmrc.centralreferencedatainboundorchestrator.repositories.{EISWorkItemRepository, MessageWrapperRepository}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, WorkItem}

import java.time.{Instant, LocalDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.xml.Elem

class SdesServiceSpec extends AnyWordSpec, GuiceOneAppPerSuite, BeforeAndAfterEach, Matchers, ScalaFutures:

  given ExecutionContext = ExecutionContext.global

  given HeaderCarrier = HeaderCarrier()

  lazy val mockMessageWrapperRepository: MessageWrapperRepository = mock[MessageWrapperRepository]
  lazy val mockEISWorkItemRepository: EISWorkItemRepository       = mock[EISWorkItemRepository]
  lazy val mockEisConnector: EisConnector                         = mock[EisConnector]
  lazy val mockAuditHandler: AuditHandler                         = mock[AuditHandler]
  lazy val mockAppConfig: AppConfig                               = mock[AppConfig]

  when(mockAppConfig.maxRetryCount).thenReturn(3)

  given mat: Materializer = app.injector.instanceOf[Materializer]

  private val sdesService =
    new SdesService(
      mockMessageWrapperRepository,
      mockEISWorkItemRepository,
      mockEisConnector,
      mockAppConfig
    )

  private val testBody: Elem                = <Body></Body>
  private val testExportRequest: EISRequest = EISRequest("<Body></Body>", "correlationID", ReferenceDataExport)

  private val testSubscriptionPayload = """<soap:Envelope xmlns:soap="http://www.w3.org/2003/05/soap-envelope"
               xmlns:csrd="http://xmlns.ec.eu/CallbackService/CSRD2/IReferenceDataSubscriptionReceiverCBS/V4"
               xmlns:msg="http://xmlns.ec.eu/BusinessObjects/CSRD2/ReferenceDataSubscriptionReceiverCBSServiceType/V4"
               xmlns:hdr="http://xmlns.ec.eu/BusinessObjects/CSRD2/MessageHeaderType/V2"
               xmlns:rdlist="http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntityEntryListType/V3"
               xmlns:rdentity="http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntityType/V3"
               xmlns:rdentry="http://xmlns.ec.eu/BusinessObjects/CSRD2/RDEntryType/V3"
               xmlns:rdstatus="http://xmlns.ec.eu/BusinessObjects/CSRD2/RDStatusType/V3"
               xmlns:lsd="http://xmlns.ec.eu/BusinessObjects/CSRD2/LsdListType/V2"
               xmlns:wsa="http://www.w3.org/2005/08/addressing">
  <soap:Header>
    <wsa:Action>CCN2.Service.Customs.Default.CSRD.ReferenceDataSubscriptionReceiverCBS/ReceiveReferenceData</wsa:Action>
    <wsa:MessageID>test-msg-123</wsa:MessageID>
  </soap:Header>
  <soap:Body>
    <csrd:ReceiveReferenceDataReqMsg>
      <msg:ReceiveReferenceDataRequestType>
        <msg:MessageHeader>
          <hdr:MessageID>MSG-TEST-001</hdr:MessageID>
          <hdr:MessageTimestamp>2024-01-01T10:00:00Z</hdr:MessageTimestamp>
          <hdr:SenderID>TEST_SENDER</hdr:SenderID>
          <hdr:ReceiverID>TEST_RECEIVER</hdr:ReceiverID>
        </msg:MessageHeader>
        <msg:RDEntityList>
          <rdlist:RDEntity>
            <rdentity:name>TestEntity</rdentity:name>
            <rdentity:version>1.0</rdentity:version>
            <rdentity:RDEntry>
              <rdentry:RDEntryStatus>
                <rdstatus:status>Active</rdstatus:status>
                <rdstatus:validFrom>2024-01-01</rdstatus:validFrom>
              </rdentry:RDEntryStatus>
              <rdentry:dataItem name="code">TEST</rdentry:dataItem>
              <rdentry:LsdList>
                <lsd:Lsd>
                  <lsd:languageCode>EN</lsd:languageCode>
                  <lsd:description>Test</lsd:description>
                </lsd:Lsd>
              </rdentry:LsdList>
            </rdentity:RDEntry>
          </rdlist:RDEntity>
        </msg:RDEntityList>
      </msg:ReceiveReferenceDataRequestType>
    </csrd:ReceiveReferenceDataReqMsg>
  </soap:Body>
</soap:Envelope>"""

  private val testSubscriptionRequest: EISRequest =
    EISRequest(testSubscriptionPayload, "correlationID", ReferenceDataSubscription)

  private def messageWrapper(id: String, messageType: SoapAction = ReferenceDataExport) =
    MessageWrapper(id, testExportRequest.payload, Received, messageType)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(
      mockMessageWrapperRepository,
      mockEISWorkItemRepository,
      mockEisConnector
    )
  }

  "SdesService" should {
    "should forward message to EIS when accepting a FileProcessed notification" in {
      val uid     = UUID.randomUUID().toString
      val message = messageWrapper(uid)

      when(mockMessageWrapperRepository.findByUid(eqTo(uid))(using any()))
        .thenReturn(Future.successful(Some(message)))

      val expectedRequest: EISRequest = EISRequest(message.payload, uid, ReferenceDataExport)

      val wi = WorkItem(
        new ObjectId(),
        Instant.now(),
        Instant.now(),
        Instant.now(),
        ProcessingStatus.ToDo,
        0,
        expectedRequest
      )

      when(mockEISWorkItemRepository.set(eqTo(expectedRequest)))
        .thenReturn(Future.successful(wi))

      val result = sdesService
        .processCallback(
          SdesCallbackResponse(
            "FileProcessed",
            "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip",
            uid,
            LocalDateTime.now(),
            Option("894bed34007114b82fa39e05197f9eec"),
            Option("MD5"),
            Option(LocalDateTime.now()),
            List(Property("name1", "value1")),
            Option("None")
          )
        )
        .futureValue

      result shouldBe s"Message with UID: $uid, successfully queued"

      verify(mockMessageWrapperRepository, times(0)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(1)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(1)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any, any)(using any(), any())
    }

    "should return av scan passed when accepting a FileReceived notification" in {
      val uid = "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"
      when(mockMessageWrapperRepository.updateStatus(eqTo(uid), eqTo(Pass))(using any()))
        .thenReturn(Future.successful(true))

      val result = sdesService
        .processCallback(
          SdesCallbackResponse(
            "FileReceived",
            "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip",
            uid,
            LocalDateTime.now(),
            Option("894bed34007114b82fa39e05197f9eec"),
            Option("MD5"),
            Option(LocalDateTime.now()),
            List(Property("name1", "value1")),
            Option("None")
          )
        )
        .futureValue

      result shouldBe "status updated to failed for uid: 32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"

      verify(mockMessageWrapperRepository, times(1)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any, any)(using any(), any())
    }

    "should return av scan failed when accepting a FileProcessingFailure notification" in {
      val uid = "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"
      when(mockMessageWrapperRepository.updateStatus(eqTo(uid), eqTo(Fail))(using any()))
        .thenReturn(Future.successful(true))

      val result = sdesService
        .processCallback(
          SdesCallbackResponse(
            "FileProcessingFailure",
            "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip",
            uid,
            LocalDateTime.now(),
            Option("894bed34007114b82fa39e05197f9eec"),
            Option("MD5"),
            Option(LocalDateTime.now()),
            List(Property("name1", "value1")),
            Option("None")
          )
        )
        .futureValue

      result shouldBe "status updated to failed for uid: 32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"

      verify(mockMessageWrapperRepository, times(1)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any, any)(using any(), any())
    }

    "should fail if SDES notification is not valid" in {
      val uid = "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"

      val result = sdesService.processCallback(
        SdesCallbackResponse(
          "FileProcessingTest",
          "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip",
          uid,
          LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"),
          Option("MD5"),
          Option(LocalDateTime.now()),
          List(Property("name1", "value1")),
          Option("None")
        )
      )

      recoverToExceptionIf[Throwable](result).map { rt =>
        rt.getMessage shouldBe "SDES notification not recognised: FileProcessingTest"
      }.futureValue

      verify(mockMessageWrapperRepository, times(0)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any, any)(using any(), any())
    }

    "forward a ReferenceDataExport payload to EIS" in {
      when(mockEisConnector.forwardMessage(eqTo(ReferenceDataExport), eqTo(testBody))(using any(), any()))
        .thenReturn(Future.successful(true))

      val result = sdesService.sendMessage(testExportRequest).futureValue

      result shouldBe true

      verify(mockMessageWrapperRepository, times(0)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(1)).forwardMessage(eqTo(ReferenceDataExport), any)(using any(), any())
    }

    "forward a ReferenceDataSubscription payload to EIS after conversion" in {
      when(mockEisConnector.forwardMessage(eqTo(ReferenceDataSubscription), any)(using any(), any()))
        .thenReturn(Future.successful(true))

      val result = sdesService.sendMessage(testSubscriptionRequest).futureValue

      result shouldBe true

      verify(mockMessageWrapperRepository, times(0)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(1)).forwardMessage(eqTo(ReferenceDataSubscription), any)(using any(), any())
    }

    "fail when sendMessage receives an unsupported message type" in {
      val unsupportedRequest = EISRequest("<Body></Body>", "correlationID", null)

      val result = sdesService.sendMessage(unsupportedRequest)

      recoverToExceptionIf[Exception](result).map { ex =>
        ex.getMessage shouldBe "Message type is not supported"
      }.futureValue

      verify(mockEisConnector, times(0)).forwardMessage(any, any)(using any(), any())
    }

    "update wrapper status on successful call to EIS" in {
      val uid = UUID.randomUUID().toString

      when(mockMessageWrapperRepository.updateStatus(eqTo(uid), eqTo(Sent))(using any()))
        .thenReturn(Future.successful(true))

      val result = sdesService.updateStatus(true, uid).futureValue

      result shouldBe s"Message with UID: $uid, successfully sent to EIS and status updated to sent."

      verify(mockMessageWrapperRepository, times(1)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any, any)(using any(), any())
    }

    "update wrapper status on successful call to EIS with status update failure" in {
      val uid = UUID.randomUUID().toString

      when(mockMessageWrapperRepository.updateStatus(eqTo(uid), eqTo(Sent))(using any()))
        .thenReturn(Future.successful(false))

      val result = sdesService.updateStatus(true, uid)

      recoverToExceptionIf[MongoWriteError](result).map { mwe =>
        mwe.message shouldBe s"failed to update message wrappers status to failed with uid: $uid"
      }.futureValue

      verify(mockMessageWrapperRepository, times(1)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any, any)(using any(), any())
    }

    "update wrapper status on failed call to EIS" in {
      val uid = UUID.randomUUID().toString

      when(mockMessageWrapperRepository.updateStatus(eqTo(uid), eqTo(Sent))(using any()))
        .thenReturn(Future.successful(false))

      val result = sdesService.updateStatus(false, uid)

      recoverToExceptionIf[EisResponseError](result).map { mwe =>
        mwe.message shouldBe s"Unable to send message to EIS after 3 attempts"
      }.futureValue

      verify(mockMessageWrapperRepository, times(0)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any, any)(using any(), any())
    }

    "should return exception MongoWriteError when accepting a FileProcessingFailure notification but Mongo fails to write" in {
      val uid = "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d"
      when(mockMessageWrapperRepository.updateStatus(eqTo(uid), eqTo(Fail))(using any()))
        .thenReturn(Future.successful(false))

      val result = sdesService.processCallback(
        SdesCallbackResponse(
          "FileProcessingFailure",
          "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip",
          uid,
          LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"),
          Option("MD5"),
          Option(LocalDateTime.now()),
          List(Property("name1", "value1")),
          Option("None")
        )
      )

      recoverToExceptionIf[MongoWriteError](result).map { mwe =>
        mwe.message shouldBe s"failed to update message wrappers status to failed with uid: $uid"
      }.futureValue

      verify(mockMessageWrapperRepository, times(1)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(0)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any, any)(using any(), any())
    }

    "should return exception NoMatchingUIDInMongoError when forwarding a message but Mongo fails to find UID in Mongo Matching" in {
      val uid = UUID.randomUUID().toString

      when(mockMessageWrapperRepository.findByUid(eqTo(uid))(using any()))
        .thenReturn(Future.successful(None))

      val result = sdesService.processCallback(
        SdesCallbackResponse(
          "FileProcessed",
          "32f2c4f7-c635-45e0-bee2-0bdd97a4a70d.zip",
          uid,
          LocalDateTime.now(),
          Option("894bed34007114b82fa39e05197f9eec"),
          Option("MD5"),
          Option(LocalDateTime.now()),
          List(Property("name1", "value1")),
          Option("None")
        )
      )

      recoverToExceptionIf[NoMatchingUIDInMongoError](result).map { mwe =>
        mwe.message shouldBe s"Failed to find a UID in Mongo matching: $uid"
      }.futureValue

      verify(mockMessageWrapperRepository, times(0)).updateStatus(any, any)(using any())
      verify(mockMessageWrapperRepository, times(1)).findByUid(any)(using any())
      verify(mockEISWorkItemRepository, times(0)).set(any)
      verify(mockEisConnector, times(0)).forwardMessage(any, any)(using any(), any())
    }
  }
