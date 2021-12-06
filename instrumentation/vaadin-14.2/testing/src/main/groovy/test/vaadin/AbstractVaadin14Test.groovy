/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.vaadin


import com.vaadin.flow.server.Version
import io.opentelemetry.api.trace.SpanKind

abstract class AbstractVaadin14Test extends AbstractVaadinTest {
  static final boolean VAADIN_14_4 = Version.majorVersion >= 2 && Version.minorVersion >= 4

  List<String> getRequestHandlers() {
    List<String> handlers = [
      "PushRequestHandler"
    ]
    if (VAADIN_14_4) {
      handlers.add("DevModeHandler")
    }
    handlers.addAll([
      "StreamRequestHandler", "UnsupportedBrowserHandler", "UidlRequestHandler",
      "HeartbeatHandler", "SessionRequestHandler", "FaviconHandler", "BootstrapHandler"
    ])

    return handlers
  }

  @Override
  void assertFirstRequest() {
    assertTraces(VAADIN_14_4 ? 5 : 4) {
      traces.sort(orderByRootSpanName(getContextPath() + "/main", getContextPath() + "/*"))

      def handlers = getRequestHandlers("BootstrapHandler")
      trace(0, 2 + handlers.size()) {
        serverSpan(it, 0, getContextPath() + "/main")
        span(1) {
          name "SpringVaadinServletService.handleRequest"
          kind SpanKind.INTERNAL
          childOf span(0)
        }

        int spanIndex = 2
        handlers.each { handler ->
          span(spanIndex++) {
            name handler + ".handleRequest"
            kind SpanKind.INTERNAL
            childOf span(1)
          }
        }
      }
      // following traces are for javascript files used on page
      def count = VAADIN_14_4 ? 3 : 2
      for (i in 0..count) {
        trace(1 + i, 1) {
          serverSpan(it, 0, getContextPath() + "/*")
        }
      }
    }
  }

  @Override
  void assertButtonClick() {
    assertTraces(1) {
      def handlers = getRequestHandlers("UidlRequestHandler")
      trace(0, 2 + handlers.size() + 1) {
        serverSpan(it, 0, getContextPath() + "/main")
        span(1) {
          name "SpringVaadinServletService.handleRequest"
          kind SpanKind.INTERNAL
          childOf span(0)
        }

        int spanIndex = 2
        handlers.each { handler ->
          span(spanIndex++) {
            name handler + ".handleRequest"
            kind SpanKind.INTERNAL
            childOf span(1)
          }
        }
        span(spanIndex) {
          name "EventRpcHandler.handle/click"
          kind SpanKind.INTERNAL
          childOf span(spanIndex - 1)
        }
      }
    }
  }
}
