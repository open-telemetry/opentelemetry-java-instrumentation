/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package filter

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.sdk.trace.data.SpanData
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.REDIRECT
import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.SUCCESS

abstract class AbstractServletFilterTest extends HttpServerTest<ConfigurableApplicationContext> implements AgentTestTrait {

  abstract Class<?> securityConfigClass()

  abstract Class<?> filterConfigClass()

  @Override
  ConfigurableApplicationContext startServer(int port) {
    def app = new SpringApplication(FilteredAppConfig, securityConfigClass(), filterConfigClass())
    app.setDefaultProperties(["server.port": port, "server.error.include-message": "always"])
    def context = app.run()
    return context
  }

  @Override
  void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close()
  }

  @Override
  boolean hasHandlerSpan(ServerEndpoint endpoint) {
    endpoint == NOT_FOUND
  }

  @Override
  boolean hasErrorPageSpans(ServerEndpoint endpoint) {
    endpoint == ERROR || endpoint == EXCEPTION || endpoint == NOT_FOUND
  }

  @Override
  boolean hasResponseSpan(ServerEndpoint endpoint) {
    endpoint == REDIRECT || endpoint == ERROR || endpoint == NOT_FOUND
  }

  @Override
  void responseSpan(TraceAssert trace, int index, Object parent, String method, ServerEndpoint endpoint) {
    switch (endpoint) {
      case REDIRECT:
        redirectSpan(trace, index, parent)
        break
      case ERROR:
      case NOT_FOUND:
        sendErrorSpan(trace, index, parent)
        break
    }
  }

  @Override
  boolean testPathParam() {
    true
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint) {
    trace.span(index) {
      name "ResourceHttpRequestHandler.handleRequest"
      kind INTERNAL
      childOf((SpanData) parent)
    }
  }

  @Override
  String expectedHttpRoute(ServerEndpoint endpoint, String method) {
    switch (endpoint) {
      case PATH_PARAM:
        return getContextPath() + "/path/{id}/param"
      case NOT_FOUND:
        return getContextPath() + "/**"
      default:
        return super.expectedHttpRoute(endpoint, method)
    }
  }

  @Override
  void errorPageSpans(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "BasicErrorController.error"
      kind INTERNAL
      childOf((SpanData) parent)
      attributes {
      }
    }
  }
}
