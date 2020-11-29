/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.filter

import static io.opentelemetry.api.trace.Span.Kind.INTERNAL
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.ERROR
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.SUCCESS

import com.google.common.collect.ImmutableMap
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import test.boot.SecurityConfig

class ServletFilterTest extends HttpServerTest<ConfigurableApplicationContext> {

  @Override
  ConfigurableApplicationContext startServer(int port) {
    def app = new SpringApplication(FilteredAppConfig, SecurityConfig)
    app.setDefaultProperties(ImmutableMap.of("server.port", port, "server.error.include-message", "always"))
    def context = app.run()
    return context
  }

  @Override
  void stopServer(ConfigurableApplicationContext ctx) {
    ctx.close()
  }

  @Override
  boolean hasHandlerSpan() {
    false
  }

  @Override
  boolean hasErrorPageSpans(ServerEndpoint endpoint) {
    endpoint == ERROR || endpoint == EXCEPTION
  }

  @Override
  boolean testPathParam() {
    true
  }

  @Override
  boolean testExceptionBody() {
    false
  }

  @Override
  boolean testNotFound() {
    // FIXME: the instrumentation adds an extra controller span which is not consistent.
    // Fix tests or remove extra span.
    false
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "TestController.${endpoint.name().toLowerCase()}"
      kind INTERNAL
      errored endpoint == EXCEPTION
      childOf((SpanData) parent)
      if (endpoint == EXCEPTION) {
        errorEvent(Exception, EXCEPTION.body)
      }
    }
  }

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    return endpoint == PATH_PARAM ? "/path/{id}/param" : endpoint.resolvePath(address).path
  }

  @Override
  void errorPageSpans(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint = SUCCESS) {
    trace.span(index) {
      name "BasicErrorController.error"
      kind INTERNAL
      errored false
      childOf((SpanData) parent)
      attributes {
      }
    }
  }
}
