/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import filter.AbstractServletFilterTest
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint
import io.opentelemetry.sdk.trace.data.SpanData

import static io.opentelemetry.api.trace.SpanKind.INTERNAL

class ServletFilterTest extends AbstractServletFilterTest {

  Class<?> securityConfigClass() {
    SecurityConfig
  }

  Class<?> filterConfigClass() {
    ServletFilterConfig
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

  @Override
  void responseSpan(TraceAssert trace, int index, Object parent, String method, ServerEndpoint endpoint) {
    if (Boolean.getBoolean("testLatestDeps") && endpoint == ServerEndpoint.NOT_FOUND) {
      trace.span(index) {
        name ~/\.sendError$/
        kind SpanKind.INTERNAL
        // not verifying the parent span, in the latest version the responseSpan is the child of the SERVER span, not the handler span
      }
    } else {
      super.responseSpan(trace, index, parent, method, endpoint)
    }
  }
}
