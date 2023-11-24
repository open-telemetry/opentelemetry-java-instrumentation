/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import boot.AbstractSpringBootBasedTest
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.sdk.trace.data.SpanData

import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class SpringBootBasedTest extends AbstractSpringBootBasedTest {

  Class<?> securityConfigClass() {
    SecurityConfig
  }

  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method = "GET", ServerEndpoint endpoint) {
    if (Boolean.getBoolean("testLatestDeps") && endpoint == ServerEndpoint.NOT_FOUND) {
      trace.span(index) {
        name "ResourceHttpRequestHandler.handleRequest"
        kind INTERNAL
        childOf((SpanData) parent)
        status StatusCode.ERROR
        errorEventWithAnyMessage Class.forName("org.springframework.web.servlet.resource.NoResourceFoundException")
      }
    } else {
      super.handlerSpan(trace, index, parent, method, endpoint)
    }
  }
}
