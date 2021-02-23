/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.test.AgentInstrumentationSpecification
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTestTrait
import io.opentelemetry.sdk.trace.data.SpanData
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.opentelemetry.test.hello_web_service.HelloRequest
import io.opentelemetry.test.hello_web_service.Hello2Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.util.resource.Resource
import org.eclipse.jetty.webapp.WebAppContext
import org.springframework.oxm.jaxb.Jaxb2Marshaller
import org.springframework.util.ClassUtils
import org.springframework.ws.client.core.WebServiceTemplate
import org.springframework.ws.soap.client.SoapFaultClientException
import spock.lang.Shared
import spock.lang.Unroll

abstract class AbstractJaxWsTest extends AgentInstrumentationSpecification implements HttpServerTestTrait<Server> {

  @Shared
  private Jaxb2Marshaller marshaller = new Jaxb2Marshaller()

  @Shared
  protected WebServiceTemplate webServiceTemplate = new WebServiceTemplate(marshaller)

  def setupSpec() {
    marshaller.setPackagesToScan(ClassUtils.getPackageName(HelloRequest))
    marshaller.afterPropertiesSet()
  }

  @Override
  Server startServer(int port) {
    List<String> configurationClasses = new ArrayList<>()
    Collections.addAll(configurationClasses, WebAppContext.getDefaultConfigurationClasses())

    WebAppContext webAppContext = new WebAppContext()
    webAppContext.setContextPath(getContextPath())
    webAppContext.setConfigurationClasses(configurationClasses)
    // set up test application
    webAppContext.setBaseResource(Resource.newSystemResource("test-app"))
    webAppContext.getMetaData().getWebInfClassesDirs().add(Resource.newClassPathResource("/"))

    def jettyServer = new Server(port)
    jettyServer.connectors.each {
      it.setHost('localhost')
    }

    jettyServer.setHandler(webAppContext)
    jettyServer.start()

    return jettyServer
  }

  @Override
  void stopServer(Server server) {
    server.stop()
    server.destroy()
  }

  @Override
  String getContextPath() {
    return "/jetty-context"
  }

  String getServiceAddress(String serviceName) {
    return address.resolve("ws/" + serviceName).toString()
  }

  def makeRequest(methodName, name) {
    Object request = null
    if ("hello" == methodName) {
      request = new HelloRequest(name: name)
    } else if ("hello2" == methodName) {
      request = new Hello2Request(name: name)
    } else {
      throw new IllegalArgumentException(methodName)
    }

    return webServiceTemplate.marshalSendAndReceive(getServiceAddress("HelloService"), request)
  }

  @Unroll
  def "test #methodName"() {
    setup:
    def response = makeRequest(methodName, "Test")

    expect:
    response.getMessage() == "Hello Test"

    and:
    def spanCount = 2
    if (hasAnnotationHandlerSpan(methodName)) {
      spanCount++
    }
    assertTraces(1) {
      trace(0, spanCount) {
        serverSpan(it, 0, serverSpanName(methodName))
        handlerSpan(it, 1, methodName, span(0))
        if (hasAnnotationHandlerSpan(methodName)) {
          annotationHandlerSpan(it, 2, methodName, span(1))
        }
      }
    }

    where:
    methodName << ["hello", "hello2"]
  }

  @Unroll
  def "test #methodName exception"() {
    when:
    makeRequest(methodName, "exception")

    then:
    def error = thrown(SoapFaultClientException)
    error.getMessage() == "hello exception"

    and:
    def spanCount = 2
    if (hasAnnotationHandlerSpan(methodName)) {
      spanCount++
    }
    def expectedException = new Exception("hello exception")
    assertTraces(1) {
      trace(0, spanCount) {
        serverSpan(it, 0, serverSpanName(methodName), expectedException)
        handlerSpan(it, 1, methodName, span(0), expectedException)
        if (hasAnnotationHandlerSpan(methodName)) {
          annotationHandlerSpan(it, 2, methodName, span(1), expectedException)
        }
      }
    }

    where:
    methodName << ["hello", "hello2"]
  }

  def hasAnnotationHandlerSpan(methodName) {
    methodName == "hello"
  }

  def serverSpanName(String operation) {
    if (operation == "hello") {
      return "HelloServiceImpl." + operation
    }
    return getContextPath() + "/ws/HelloService/" + operation
  }

  static serverSpan(TraceAssert trace, int index, String operation, Throwable exception = null) {
    trace.span(index) {
      hasNoParent()
      name operation
      kind SpanKind.SERVER
      errored exception != null
    }
  }

  static handlerSpan(TraceAssert trace, int index, String operation, Object parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      name "HelloService/" + operation
      kind SpanKind.INTERNAL
      errored exception != null
      if (exception) {
        errorEvent(exception.class, exception.message)
      }
    }
  }

  static annotationHandlerSpan(TraceAssert trace, int index, String methodName, Object parentSpan = null, Throwable exception = null) {
    trace.span(index) {
      if (parentSpan == null) {
        hasNoParent()
      } else {
        childOf((SpanData) parentSpan)
      }
      name "HelloServiceImpl." + methodName
      kind SpanKind.INTERNAL
      errored exception != null
      if (exception) {
        errorEvent(exception.class, exception.message)
      }
      attributes {
        "${SemanticAttributes.CODE_NAMESPACE.key}" "hello.HelloServiceImpl"
        "${SemanticAttributes.CODE_FUNCTION.key}" methodName
      }
    }
  }
}
