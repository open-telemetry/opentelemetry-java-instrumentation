package test

import datadog.opentracing.DDSpan
import datadog.trace.agent.test.asserts.ListWriterAssert
import datadog.trace.agent.test.asserts.SpanAssert
import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.api.DDSpanTypes
import datadog.trace.instrumentation.servlet3.Servlet3Decorator
import datadog.trace.instrumentation.springweb.SpringWebHttpServerDecorator
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import io.opentracing.tag.Tags
import org.apache.catalina.core.ApplicationFilterChain
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.web.servlet.view.RedirectView

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.SUCCESS
import static java.util.Collections.singletonMap

class SpringBootBasedTest extends HttpServerTest<ConfigurableApplicationContext, Servlet3Decorator> {

  @Override
  ConfigurableApplicationContext startServer(int port) {
    def app = new SpringApplication(AppConfig)
    app.setDefaultProperties(singletonMap("server.port", port))
    def context = app.run()
    return context
  }

  @Override
  void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close()
  }

  @Override
  Servlet3Decorator decorator() {
    return Servlet3Decorator.DECORATE
  }

  @Override
  String expectedOperationName() {
    return "servlet.request"
  }

  @Override
  boolean hasHandlerSpan() {
    true
  }

  @Override
  boolean testNotFound() {
    // FIXME: the instrumentation adds an extra controller span which is not consistent.
    // Fix tests or remove extra span.
    false
  }

  void cleanAndAssertTraces(
    final int size,
    @ClosureParams(value = SimpleType, options = "datadog.trace.agent.test.asserts.ListWriterAssert")
    @DelegatesTo(value = ListWriterAssert, strategy = Closure.DELEGATE_FIRST)
    final Closure spec) {

    // If this is failing, make sure HttpServerTestAdvice is applied correctly.
    TEST_WRITER.waitForTraces(size * 2)

    TEST_WRITER.each {
      def renderSpan = it.find {
        it.operationName == "response.render"
      }
      if (renderSpan) {
        SpanAssert.assertSpan(renderSpan) {
          operationName "response.render"
          resourceName "response.render"
          spanType "web"
          errored false
          tags {
            "component" "spring-webmvc"
            "view.type" RedirectView.name
            "span.kind" "server"
            defaultTags()
          }
        }
        it.remove(renderSpan)
      }
    }

    super.cleanAndAssertTraces(size, spec)
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      serviceName expectedServiceName()
      operationName "TestController.${endpoint.name().toLowerCase()}"
      spanType DDSpanTypes.HTTP_SERVER
      errored endpoint == EXCEPTION
      childOf(parent as DDSpan)
      tags {
        "$Tags.COMPONENT.key" SpringWebHttpServerDecorator.DECORATE.component()
        "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
        defaultTags()
        if (endpoint == EXCEPTION) {
          errorTags(Exception, EXCEPTION.body)
        }
      }
    }
  }

  @Override
  void serverSpan(TraceAssert trace, int index, String traceID = null, String parentID = null, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      serviceName expectedServiceName()
      operationName expectedOperationName()
      resourceName endpoint.status == 404 ? "404" : "$method ${endpoint.resolve(address).path}"
      spanType DDSpanTypes.HTTP_SERVER
      errored endpoint.errored
      if (parentID != null) {
        traceId traceID
        parentId parentID
      } else {
        parent()
      }
      tags {
        "span.origin.type" ApplicationFilterChain.name

        defaultTags(true)
        "$Tags.COMPONENT.key" serverDecorator.component()
        if (endpoint.errored) {
          "$Tags.ERROR.key" endpoint.errored
          "error.msg" { it == null || it == EXCEPTION.body }
          "error.type" { it == null || it == Exception.name }
          "error.stack" { it == null || it instanceof String }
        }
        "$Tags.HTTP_STATUS.key" endpoint.status
        "$Tags.HTTP_URL.key" "${endpoint.resolve(address)}"
        "$Tags.PEER_HOSTNAME.key" { it == "localhost" || it == "127.0.0.1" }
        "$Tags.PEER_PORT.key" Integer
        "$Tags.PEER_HOST_IPV4.key" { it == null || it == "127.0.0.1" } // Optional
        "$Tags.HTTP_METHOD.key" method
        "$Tags.SPAN_KIND.key" Tags.SPAN_KIND_SERVER
      }
    }
  }
}
