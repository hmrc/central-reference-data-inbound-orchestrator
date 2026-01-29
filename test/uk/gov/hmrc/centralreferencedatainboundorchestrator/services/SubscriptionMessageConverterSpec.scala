/*
 * Copyright 2026 HM Revenue & Customs
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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.xml.XML

class SubscriptionMessageConverterSpec extends AnyWordSpec with Matchers {

  "SubscriptionMessageConverter" should {

    "successfully convert SOAP message with data to HMRC format" in {
      val soapXml = """<?xml version="1.0" encoding="UTF-8"?>
                      |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                      |  <soapenv:Header/>
                      |  <soapenv:Body>
                      |    <csrd:ReceiveReferenceDataReqMsg xmlns:csrd="http://xmlns.ec.eu/CSR">
                      |      <msg:ReceiveReferenceDataRequestType xmlns:msg="http://xmlns.ec.eu/message">
                      |        <MessageHeader>
                      |          <MessageID>MSG123456</MessageID>
                      |          <MessageTimestamp>2026-01-25T10:30:00Z</MessageTimestamp>
                      |          <SenderID>SENDER001</SenderID>
                      |          <ReceiverID>RECEIVER001</ReceiverID>
                      |        </MessageHeader>
                      |        <RDEntityList>
                      |          <RDEntity name="CountryCodesFullList">
                      |            <RDEntry>
                      |              <status>valid</status>
                      |              <activeFrom>2026-01-01</activeFrom>
                      |              <dataItem name="code">GB</dataItem>
                      |              <LsdList>
                      |                <description lang="en">United Kingdom</description>
                      |              </LsdList>
                      |            </RDEntry>
                      |          </RDEntity>
                      |        </RDEntityList>
                      |      </msg:ReceiveReferenceDataRequestType>
                      |    </csrd:ReceiveReferenceDataReqMsg>
                      |  </soapenv:Body>
                      |</soapenv:Envelope>""".stripMargin

      val result    = SubscriptionMessageConverter.convertSoapString(soapXml)
      val resultXml = XML.loadString(result)

      resultXml.label shouldBe "HMRCReceiveReferenceDataReqMsg"

      (resultXml \\ "AddressingInformation" \\ "messageID").text.trim shouldBe "MSG123456"

      val rdEntity = (resultXml \\ "RDEntity").head
      (rdEntity \@ "name") shouldBe "CountryCodesFullList"

      (resultXml \\ "RDEntry" \\ "RDEntryStatus" \\ "state").text.trim      shouldBe "valid"
      (resultXml \\ "RDEntry" \\ "RDEntryStatus" \\ "activeFrom").text.trim shouldBe "2026-01-01"

      val description = (resultXml \\ "LsdList" \\ "description").head
      description.text.trim   shouldBe "United Kingdom"
      (description \@ "lang") shouldBe "en"
    }

    "convert SOAP message with multiple entities" in {
      val soapXml = """<?xml version="1.0" encoding="UTF-8"?>
                      |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                      |  <soapenv:Body>
                      |    <csrd:ReceiveReferenceDataReqMsg xmlns:csrd="http://xmlns.ec.eu/CSR">
                      |      <msg:ReceiveReferenceDataRequestType xmlns:msg="http://xmlns.ec.eu/message">
                      |        <MessageHeader>
                      |          <MessageID>MSG789</MessageID>
                      |          <MessageTimestamp>2026-01-25T11:00:00Z</MessageTimestamp>
                      |          <SenderID>SENDER002</SenderID>
                      |          <ReceiverID>RECEIVER002</ReceiverID>
                      |        </MessageHeader>
                      |        <RDEntityList>
                      |          <RDEntity name="CountryCodesFullList">
                      |            <RDEntry>
                      |              <status>valid</status>
                      |              <activeFrom>2026-01-01</activeFrom>
                      |              <dataItem name="code">GB</dataItem>
                      |              <LsdList>
                      |                <description lang="en">United Kingdom</description>
                      |              </LsdList>
                      |            </RDEntry>
                      |          </RDEntity>
                      |          <RDEntity name="CountryCodesFullList">
                      |            <RDEntry>
                      |              <status>valid</status>
                      |              <activeFrom>2026-01-01</activeFrom>
                      |              <dataItem name="code">FR</dataItem>
                      |              <LsdList>
                      |                <description lang="en">France</description>
                      |              </LsdList>
                      |            </RDEntry>
                      |          </RDEntity>
                      |        </RDEntityList>
                      |      </msg:ReceiveReferenceDataRequestType>
                      |    </csrd:ReceiveReferenceDataReqMsg>
                      |  </soapenv:Body>
                      |</soapenv:Envelope>""".stripMargin

      val result    = SubscriptionMessageConverter.convertSoapString(soapXml)
      val resultXml = XML.loadString(result)

      val entities = resultXml \\ "RDEntity"
      entities.size shouldBe 2

      (entities.head \@ "name") shouldBe "CountryCodesFullList"
      (entities.last \@ "name") shouldBe "CountryCodesFullList"

      (entities.head \\ "LsdList" \\ "description").text.trim shouldBe "United Kingdom"

      (entities.last \\ "LsdList" \\ "description").text.trim shouldBe "France"
    }

    "handle SOAP message with error report" in {
      val soapXml = """<?xml version="1.0" encoding="UTF-8"?>
                      |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                      |  <soapenv:Body>
                      |    <csrd:ReceiveReferenceDataReqMsg xmlns:csrd="http://xmlns.ec.eu/CSR">
                      |      <msg:ReceiveReferenceDataRequestType xmlns:msg="http://xmlns.ec.eu/message">
                      |        <MessageHeader>
                      |          <MessageID>780912</MessageID>
                      |          <MessageTimestamp>2026-01-25T12:00:00Z</MessageTimestamp>
                      |          <SenderID>SENDER003</SenderID>
                      |          <ReceiverID>RECEIVER003</ReceiverID>
                      |        </MessageHeader>
                      |        <ErrorReport>717b4129-5682-494c-bd23-87f0a297b296</ErrorReport>
                      |      </msg:ReceiveReferenceDataRequestType>
                      |    </csrd:ReceiveReferenceDataReqMsg>
                      |  </soapenv:Body>
                      |</soapenv:Envelope>""".stripMargin

      val result    = SubscriptionMessageConverter.convertSoapString(soapXml)
      val resultXml = XML.loadString(result)

      resultXml.label shouldBe "HMRCReceiveReferenceDataReqMsg"

      (resultXml \\ "AddressingInformation" \\ "messageID").text.trim shouldBe "780912"

      (resultXml \\ "ErrorReport").text.trim shouldBe "717b4129-5682-494c-bd23-87f0a297b296"

      (resultXml \\ "RDEntityList").isEmpty shouldBe true
    }

    "handle entity with alternative input structure using 'n' element" in {
      val soapXml = """<?xml version="1.0" encoding="UTF-8"?>
                      |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                      |  <soapenv:Body>
                      |    <csrd:ReceiveReferenceDataReqMsg xmlns:csrd="http://xmlns.ec.eu/CSR">
                      |      <msg:ReceiveReferenceDataRequestType xmlns:msg="http://xmlns.ec.eu/message">
                      |        <MessageHeader>
                      |          <MessageID>MSG555</MessageID>
                      |          <MessageTimestamp>2026-01-25T13:00:00Z</MessageTimestamp>
                      |          <SenderID>SENDER004</SenderID>
                      |          <ReceiverID>RECEIVER004</ReceiverID>
                      |        </MessageHeader>
                      |        <RDEntityList>
                      |          <RDEntity>
                      |            <n>TransportModeCode</n>
                      |            <RDEntry>
                      |              <status>valid</status>
                      |              <activeFrom>2026-01-01</activeFrom>
                      |              <dataItem name="code">SEA</dataItem>
                      |              <LsdList>
                      |                <description lang="en">Sea Transport</description>
                      |              </LsdList>
                      |            </RDEntry>
                      |          </RDEntity>
                      |        </RDEntityList>
                      |      </msg:ReceiveReferenceDataRequestType>
                      |    </csrd:ReceiveReferenceDataReqMsg>
                      |  </soapenv:Body>
                      |</soapenv:Envelope>""".stripMargin

      val result    = SubscriptionMessageConverter.convertSoapString(soapXml)
      val resultXml = XML.loadString(result)

      val rdEntity = (resultXml \\ "RDEntity").head
      (rdEntity \@ "name") shouldBe "TransportModeCode"

      (resultXml \\ "LsdList" \\ "description").text.trim shouldBe "Sea Transport"
    }

    "handle entity with alternative field names (status vs state, validFrom vs activeFrom)" in {
      val soapXml = """<?xml version="1.0" encoding="UTF-8"?>
                      |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                      |  <soapenv:Body>
                      |    <csrd:ReceiveReferenceDataReqMsg xmlns:csrd="http://xmlns.ec.eu/CSR">
                      |      <msg:ReceiveReferenceDataRequestType xmlns:msg="http://xmlns.ec.eu/message">
                      |        <MessageHeader>
                      |          <MessageID>MSG666</MessageID>
                      |          <MessageTimestamp>2026-01-25T14:00:00Z</MessageTimestamp>
                      |          <SenderID>SENDER005</SenderID>
                      |          <ReceiverID>RECEIVER005</ReceiverID>
                      |        </MessageHeader>
                      |        <RDEntityList>
                      |          <RDEntity name="CurrencyCode">
                      |            <RDEntry>
                      |              <state>active</state>
                      |              <validFrom>2026-01-01</validFrom>
                      |              <dataItem name="code">EUR</dataItem>
                      |              <LsdList>
                      |                <description lang="en">Euro</description>
                      |              </LsdList>
                      |            </RDEntry>
                      |          </RDEntity>
                      |        </RDEntityList>
                      |      </msg:ReceiveReferenceDataRequestType>
                      |    </csrd:ReceiveReferenceDataReqMsg>
                      |  </soapenv:Body>
                      |</soapenv:Envelope>""".stripMargin

      val result    = SubscriptionMessageConverter.convertSoapString(soapXml)
      val resultXml = XML.loadString(result)

      (resultXml \\ "RDEntryStatus" \\ "state").text.trim      shouldBe "active"
      (resultXml \\ "RDEntryStatus" \\ "activeFrom").text.trim shouldBe "2026-01-01"
    }

    "handle entity without activeFrom date" in {
      val soapXml = """<?xml version="1.0" encoding="UTF-8"?>
                      |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                      |  <soapenv:Body>
                      |    <csrd:ReceiveReferenceDataReqMsg xmlns:csrd="http://xmlns.ec.eu/CSR">
                      |      <msg:ReceiveReferenceDataRequestType xmlns:msg="http://xmlns.ec.eu/message">
                      |        <MessageHeader>
                      |          <MessageID>MSG888</MessageID>
                      |          <MessageTimestamp>2026-01-25T15:00:00Z</MessageTimestamp>
                      |          <SenderID>SENDER006</SenderID>
                      |          <ReceiverID>RECEIVER006</ReceiverID>
                      |        </MessageHeader>
                      |        <RDEntityList>
                      |          <RDEntity name="StatusCode">
                      |            <RDEntry>
                      |              <status>ACTIVE</status>
                      |              <dataItem name="code">ACT</dataItem>
                      |              <LsdList>
                      |                <description lang="en">Active Status</description>
                      |              </LsdList>
                      |            </RDEntry>
                      |          </RDEntity>
                      |        </RDEntityList>
                      |      </msg:ReceiveReferenceDataRequestType>
                      |    </csrd:ReceiveReferenceDataReqMsg>
                      |  </soapenv:Body>
                      |</soapenv:Envelope>""".stripMargin

      val result    = SubscriptionMessageConverter.convertSoapString(soapXml)
      val resultXml = XML.loadString(result)

      (resultXml \\ "RDEntryStatus" \\ "state").text.trim      shouldBe "ACTIVE"
      (resultXml \\ "RDEntryStatus" \\ "activeFrom").text.trim shouldBe ""
    }

    "throw exception for malformed SOAP message" in {
      val malformedXml = """<?xml version="1.0" encoding="UTF-8"?>
                           |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                           |  <soapenv:Body>
                           |    <InvalidElement/>
                           |  </soapenv:Body>
                           |</soapenv:Envelope>""".stripMargin

      val exception = intercept[RuntimeException] {
        SubscriptionMessageConverter.convertSoapString(malformedXml)
      }

      exception.getMessage should include("Failed to convert SOAP message")
    }

    "throw exception for invalid XML string" in {
      val invalidXml = "This is not XML"

      intercept[org.xml.sax.SAXParseException] {
        SubscriptionMessageConverter.convertSoapString(invalidXml)
      }
    }

    "handle entity without RDEntry element" in {
      val soapXml = """<?xml version="1.0" encoding="UTF-8"?>
                      |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                      |  <soapenv:Body>
                      |    <csrd:ReceiveReferenceDataReqMsg xmlns:csrd="http://xmlns.ec.eu/CSR">
                      |      <msg:ReceiveReferenceDataRequestType xmlns:msg="http://xmlns.ec.eu/message">
                      |        <MessageHeader>
                      |          <MessageID>MSG999</MessageID>
                      |          <MessageTimestamp>2026-01-25T16:00:00Z</MessageTimestamp>
                      |          <SenderID>SENDER007</SenderID>
                      |          <ReceiverID>RECEIVER007</ReceiverID>
                      |        </MessageHeader>
                      |        <RDEntityList>
                      |          <RDEntity name="EmptyEntity">
                      |          </RDEntity>
                      |        </RDEntityList>
                      |      </msg:ReceiveReferenceDataRequestType>
                      |    </csrd:ReceiveReferenceDataReqMsg>
                      |  </soapenv:Body>
                      |</soapenv:Envelope>""".stripMargin

      val result    = SubscriptionMessageConverter.convertSoapString(soapXml)
      val resultXml = XML.loadString(result)

      val rdEntity = (resultXml \\ "RDEntity").head
      (rdEntity \@ "name")                                shouldBe "EmptyEntity"
      (resultXml \\ "RDEntryStatus" \\ "state").text.trim shouldBe "valid"
    }

    "preserve namespace prefixes in output" in {
      val soapXml = """<?xml version="1.0" encoding="UTF-8"?>
                      |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                      |  <soapenv:Body>
                      |    <csrd:ReceiveReferenceDataReqMsg xmlns:csrd="http://xmlns.ec.eu/CSR">
                      |      <msg:ReceiveReferenceDataRequestType xmlns:msg="http://xmlns.ec.eu/message">
                      |        <MessageHeader>
                      |          <MessageID>MSG666</MessageID>
                      |          <MessageTimestamp>2026-01-25T17:00:00Z</MessageTimestamp>
                      |          <SenderID>SENDER008</SenderID>
                      |          <ReceiverID>RECEIVER008</ReceiverID>
                      |        </MessageHeader>
                      |        <RDEntityList>
                      |          <RDEntity name="Sample">
                      |            <RDEntry>
                      |              <status>valid</status>
                      |              <activeFrom>2026-01-01</activeFrom>
                      |              <dataItem name="code">S1</dataItem>
                      |              <LsdList>
                      |                <description lang="en">Sample</description>
                      |              </LsdList>
                      |            </RDEntry>
                      |          </RDEntity>
                      |        </RDEntityList>
                      |      </msg:ReceiveReferenceDataRequestType>
                      |    </csrd:ReceiveReferenceDataReqMsg>
                      |  </soapenv:Body>
                      |</soapenv:Envelope>""".stripMargin

      val result = SubscriptionMessageConverter.convertSoapString(soapXml)

      result should include("mh:")
      result should include("rdeelt:")
      result should include("rde:")
      result should include("rdet:")
      result should include("rdst:")
      result should include("lsdlt:")
    }

    "handle entity with missing LSD description" in {
      val soapXml = """<?xml version="1.0" encoding="UTF-8"?>
                      |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                      |  <soapenv:Body>
                      |    <csrd:ReceiveReferenceDataReqMsg xmlns:csrd="http://xmlns.ec.eu/CSR">
                      |      <msg:ReceiveReferenceDataRequestType xmlns:msg="http://xmlns.ec.eu/message">
                      |        <MessageHeader>
                      |          <MessageID>MSG555</MessageID>
                      |          <MessageTimestamp>2026-01-25T18:00:00Z</MessageTimestamp>
                      |          <SenderID>SENDER009</SenderID>
                      |          <ReceiverID>RECEIVER009</ReceiverID>
                      |        </MessageHeader>
                      |        <RDEntityList>
                      |          <RDEntity name="NoDescEntity">
                      |            <RDEntry>
                      |              <status>valid</status>
                      |              <activeFrom>2026-01-01</activeFrom>
                      |              <dataItem name="code">ND1</dataItem>
                      |              <LsdList/>
                      |            </RDEntry>
                      |          </RDEntity>
                      |        </RDEntityList>
                      |      </msg:ReceiveReferenceDataRequestType>
                      |    </csrd:ReceiveReferenceDataReqMsg>
                      |  </soapenv:Body>
                      |</soapenv:Envelope>""".stripMargin

      val result    = SubscriptionMessageConverter.convertSoapString(soapXml)
      val resultXml = XML.loadString(result)

      (resultXml \\ "LsdList" \\ "description").text.trim shouldBe "NoDescEntity"
    }

    "handle entity with different language code" in {
      val soapXml = """<?xml version="1.0" encoding="UTF-8"?>
                      |<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                      |  <soapenv:Body>
                      |    <csrd:ReceiveReferenceDataReqMsg xmlns:csrd="http://xmlns.ec.eu/CSR">
                      |      <msg:ReceiveReferenceDataRequestType xmlns:msg="http://xmlns.ec.eu/message">
                      |        <MessageHeader>
                      |          <MessageID>MSG111</MessageID>
                      |          <MessageTimestamp>2026-01-25T19:00:00Z</MessageTimestamp>
                      |          <SenderID>SENDER010</SenderID>
                      |          <ReceiverID>RECEIVER010</ReceiverID>
                      |        </MessageHeader>
                      |        <RDEntityList>
                      |          <RDEntity name="CountryCode">
                      |            <RDEntry>
                      |              <status>valid</status>
                      |              <activeFrom>2026-01-01</activeFrom>
                      |              <dataItem name="code">DE</dataItem>
                      |              <LsdList>
                      |                <description lang="de">Deutschland</description>
                      |              </LsdList>
                      |            </RDEntry>
                      |          </RDEntity>
                      |        </RDEntityList>
                      |      </msg:ReceiveReferenceDataRequestType>
                      |    </csrd:ReceiveReferenceDataReqMsg>
                      |  </soapenv:Body>
                      |</soapenv:Envelope>""".stripMargin

      val result    = SubscriptionMessageConverter.convertSoapString(soapXml)
      val resultXml = XML.loadString(result)

      val description = (resultXml \\ "LsdList" \\ "description").head
      description.text.trim   shouldBe "Deutschland"
      (description \@ "lang") shouldBe "de"
    }
  }
}
