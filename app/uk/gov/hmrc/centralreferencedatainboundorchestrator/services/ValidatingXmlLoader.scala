/*
 * Copyright 2025 HM Revenue & Customs
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

import org.xml.sax.SAXParseException

import javax.xml.XMLConstants
import javax.xml.parsers.{SAXParser, SAXParserFactory}
import javax.xml.validation.{Schema, SchemaFactory}
import scala.xml.Elem
import scala.xml.factory.XMLLoader
import scala.xml.parsing.{FactoryAdapter, NoBindingFactoryAdapter}

class ValidatingXmlLoader extends XMLLoader[Elem]:
  private lazy val soapSchema: Schema =
    val factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
    factory.newSchema(getClass.getResource("/schemas/soap-envelope.xsd"))

  private lazy val parserInstance: ThreadLocal[SAXParser] =
    ThreadLocal.withInitial { () =>
      val factory = SAXParserFactory.newInstance()
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
      factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      factory.setNamespaceAware(true)
      factory.setXIncludeAware(false)
      factory.setSchema(soapSchema)
      factory.newSAXParser()
    }

  override def adapter: FactoryAdapter = new NoBindingFactoryAdapter {
    override def error(e: SAXParseException): Unit = throw e
  }

  override def parser: SAXParser = parserInstance.get()
