/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server.base

import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.instrumentation.test.asserts.TraceAssert
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.sdk.trace.data.SpanData
import org.springframework.web.server.ResponseStatusException

import static io.opentelemetry.api.trace.SpanKind.INTERNAL
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.EXCEPTION
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND

abstract class HandlerSpringWebFluxServerTest extends SpringWebFluxServerTest {
  @Override
  void handlerSpan(TraceAssert trace, int index, Object parent, String method, HttpServerTest.ServerEndpoint endpoint) {
    def handlerSpanName = "${ServerTestRouteFactory.simpleName}.lambda"
    if (endpoint == NOT_FOUND) {
      handlerSpanName = "ResourceWebHandler.handle"
    }
    trace.span(index) {
      name handlerSpanName
      kind INTERNAL
      if (endpoint == EXCEPTION) {
        status StatusCode.ERROR
        errorEvent(IllegalStateException, EXCEPTION.body)
      } else if (endpoint == NOT_FOUND) {
        status StatusCode.ERROR
        if (Boolean.getBoolean("testLatestDeps")) {
          errorEvent(ResponseStatusException, "404 NOT_FOUND")
        } else {
          errorEvent(ResponseStatusException, "Response status 404")
        }
      }
      childOf((SpanData) parent)
    }
  }

  @Override
  boolean verifyServerSpanEndTime() {
    // TODO (trask) it seems like in this case ideally the handler span (which ends when the Mono
    //  that the handler returns completes) should end before the server span (which needs the
    //  result of the Mono)
    false
  }
}
