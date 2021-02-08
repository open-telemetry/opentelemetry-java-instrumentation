/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.boot

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.test.hello_web_service.HelloRequest
import io.opentelemetry.test.hello_web_service.HelloRequestSoapAction
import io.opentelemetry.test.hello_web_service.HelloRequestWsAction
import io.opentelemetry.test.hello_web_service.HelloResponse
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.oxm.jaxb.Jaxb2Marshaller
import org.springframework.util.ClassUtils
import org.springframework.ws.client.core.WebServiceMessageCallback
import org.springframework.ws.client.core.WebServiceTemplate
import org.springframework.ws.soap.addressing.client.ActionCallback
import org.springframework.ws.soap.client.SoapFaultClientException
import org.springframework.ws.soap.client.core.SoapActionCallback
import spock.lang.Shared

class SpringWsTest extends AgentInstrumentationSpecification implements HttpServerTestTrait<ConfigurableApplicationContext> {

  @Shared
  private Jaxb2Marshaller marshaller = new Jaxb2Marshaller()

  def setupSpec() {
    marshaller.setPackagesToScan(ClassUtils.getPackageName(HelloRequest))
    marshaller.afterPropertiesSet()
  }

  @Override
  ConfigurableApplicationContext startServer(int port) {
    def app = new SpringApplication(AppConfig, WebServiceConfig)
    app.setDefaultProperties([
      "server.port"                 : port,
      "server.context-path"         : getContextPath(),
      "server.servlet.contextPath"  : getContextPath(),
      "server.error.include-message": "always"])
    def context = app.run()
    return context
  }

  @Override
  void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close()
  }

  @Override
  String getContextPath() {
    return "/xyz"
  }

  HelloResponse makeRequest(methodName, name) {
    WebServiceTemplate webServiceTemplate = new WebServiceTemplate(marshaller)

    Object request = null
    WebServiceMessageCallback callback = null
    if ("hello" == methodName) {
      request = new HelloRequest(name: name)
    } else if ("helloSoapAction" == methodName) {
      request = new HelloRequestSoapAction(name: name)
      callback = new SoapActionCallback("http://opentelemetry.io/test/hello-soap-action")
    } else if ("helloWsAction" == methodName) {
      request = new HelloRequestWsAction(name: name)
      callback = new ActionCallback("http://opentelemetry.io/test/hello-ws-action")
    } else {
      throw new IllegalArgumentException(methodName)
    }

    return (HelloResponse) webServiceTemplate.marshalSendAndReceive(address.resolve("ws").toString(), request, callback)
  }

  def "test #methodName"(methodName) {
    setup:
    HelloResponse response = makeRequest(methodName, "Test")

    expect:
    response.getMessage() == "Hello Test"

    and:
    assertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, getContextPath() + "/ws")
        handlerSpan(it, 1, methodName, span(0))
      }
    }

    where:
    methodName << ["hello", "helloSoapAction", "helloWsAction"]
  }

  def "test #methodName exception"(methodName) {
    when:
    makeRequest(methodName, "exception")

    then:
    def error = thrown(SoapFaultClientException)
    error.getMessage() == "hello exception"

    and:
    def expectedException = new Exception("hello exception")
    assertTraces(1) {
      trace(0, 2) {
        serverSpan(it, 0, getContextPath() + "/ws", expectedException)
        handlerSpan(it, 1, methodName, span(0), expectedException)
      }
    }

    where:
    methodName << ["hello", "helloSoapAction", "helloWsAction"]
  }

  static serverSpan(TraceAssert trace, int index, String operation, Throwable exception = null) {
    trace.span(index) {
      hasNoParent()
      name operation
      kind SpanKind.SERVER
      errored exception != null
    }
  }

  static handlerSpan(TraceAssert trace, int index, String methodName, Object parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      name "HelloEndpoint." + methodName
      kind SpanKind.INTERNAL
      errored exception != null
      if (exception) {
        errorEvent(exception.class, exception.message)
      }
      attributes {
        "${SemanticAttributes.CODE_NAMESPACE.key}" "test.boot.HelloEndpoint"
        "${SemanticAttributes.CODE_FUNCTION.key}" methodName
      }
    }
  }
}
