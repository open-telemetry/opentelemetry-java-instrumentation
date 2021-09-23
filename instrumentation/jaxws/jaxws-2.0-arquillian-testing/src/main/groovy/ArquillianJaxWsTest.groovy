/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.testing.internal.armeria.client.WebClient
import org.jboss.arquillian.container.test.api.Deployment
import org.jboss.arquillian.container.test.api.RunAsClient
import org.jboss.arquillian.spock.ArquillianSputnik
import org.jboss.arquillian.test.api.ArquillianResource
import org.jboss.shrinkwrap.api.ShrinkWrap
import org.jboss.shrinkwrap.api.asset.EmptyAsset
import org.jboss.shrinkwrap.api.spec.WebArchive
import org.jsoup.Jsoup
import org.junit.runner.RunWith
import spock.lang.Unroll
import test.EjbHelloServiceImpl
import test.HelloService
import test.HelloServiceImpl

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.api.trace.SpanKind.SERVER
import static io.opentelemetry.api.trace.StatusCode.ERROR

@RunWith(ArquillianSputnik)
@RunAsClient
abstract class ArquillianJaxWsTest extends AgentInstrumentationSpecification {

  static WebClient client = WebClient.of()

  @ArquillianResource
  static URI url

  @Deployment
  static WebArchive createDeployment() {
    return ShrinkWrap.create(WebArchive)
      .addClass(HelloService)
      .addClass(HelloServiceImpl)
      .addClass(EjbHelloServiceImpl)
      .addAsWebInfResource(EmptyAsset.INSTANCE, "beans.xml")
  }

  def getContextRoot() {
    return url.getPath()
  }

  def getServicePath(String service) {
    service
  }

  def getAddress(String service) {
    return url.resolve(getServicePath(service)).toString()
  }

  @Unroll
  def "test #service"() {
    setup:
    def soapMessage =
      """<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:hel="http://opentelemetry.io/test/hello-web-service">
         <soapenv:Header/>
         <soapenv:Body>
            <hel:helloRequest>
               <name>Test</name>
            </hel:helloRequest>
         </soapenv:Body>
      </soapenv:Envelope>"""

    def response = client.post(getAddress(service), soapMessage).aggregate().join()
    def doc = Jsoup.parse(response.contentUtf8())

    expect:
    response.status().code() == 200
    doc.selectFirst("message").text() == "Hello Test"

    and:
    def methodName = "hello"
    assertTraces(1) {
      trace(0, 3) {
        serverSpan(it, 0, serverSpanName(service, methodName))
        handlerSpan(it, 1, service, methodName, span(0))
        annotationHandlerSpan(it, 2, service, methodName, span(1))
      }
    }

    where:
    service << ["HelloService", "EjbHelloService"]
  }

  def serverSpanName(String service, String operation) {
    return getContextRoot() + getServicePath(service) + "/" + service + "/" + operation
  }

  static serverSpan(TraceAssert trace, int index, String operation, Throwable exception = null) {
    trace.span(index) {
      hasNoParent()
      name operation
      kind SERVER
      if (exception != null) {
        status ERROR
      }
    }
  }

  static handlerSpan(TraceAssert trace, int index, String service, String operation, Object parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      name service + "/" + operation
      kind INTERNAL
      if (exception) {
        status ERROR
        errorEvent(exception.class, exception.message)
      }
    }
  }

  static annotationHandlerSpan(TraceAssert trace, int index, String service, String methodName, Object parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      name service + "Impl." + methodName
      kind INTERNAL
      if (exception) {
        status ERROR
        errorEvent(exception.class, exception.message)
      }
      attributes {
        "${SemanticAttributes.CODE_NAMESPACE.key}" "test." + service + "Impl"
        "${SemanticAttributes.CODE_FUNCTION.key}" methodName
      }
    }
  }
}
