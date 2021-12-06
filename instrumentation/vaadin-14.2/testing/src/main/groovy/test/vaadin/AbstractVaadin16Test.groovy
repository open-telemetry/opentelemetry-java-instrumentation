/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.vaadin


import com.vaadin.flow.server.Version
import io.opentelemetry.api.trace.SpanKind

abstract class AbstractVaadin16Test extends AbstractVaadinTest {
  static final boolean VAADIN_17 = Version.majorVersion >= 4
  static final boolean VAADIN_19 = Version.majorVersion >= 6
  static final boolean VAADIN_21 = Version.majorVersion >= 8
  static final boolean VAADIN_22 = Version.majorVersion >= 9

  @Override
  List<String> getRequestHandlers() {
    List<String> handlers = []
    if (VAADIN_22) {
      handlers.add("WebpackHandler")
    } else if (VAADIN_21) {
      handlers.add("DevModeHandlerImpl")
    }
    handlers.add("PushRequestHandler")
    if (VAADIN_19) {
      handlers.addAll("WebComponentBootstrapHandler", "WebComponentProvider", "PwaHandler")
    }
    handlers.addAll([
      "StreamRequestHandler", "UnsupportedBrowserHandler", "UidlRequestHandler",
      "HeartbeatHandler", "SessionRequestHandler", "JavaScriptBootstrapHandler", "FaviconHandler"])
    if (!VAADIN_21) {
      handlers.add("DevModeHandler")
    }
    handlers.add("IndexHtmlRequestHandler")

    return handlers
  }

  @Override
  void assertFirstRequest() {
    assertTraces(VAADIN_17 ? 9 : 8) {
      traces.sort(orderByRootSpanName("IndexHtmlRequestHandler.handleRequest",
        getContextPath() + "/main", getContextPath(), getContextPath() + "/*"))

      def handlers = getRequestHandlers("IndexHtmlRequestHandler")
      trace(0, 2 + handlers.size()) {
        serverSpan(it, 0, "IndexHtmlRequestHandler.handleRequest")
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
      handlers = getRequestHandlers("UidlRequestHandler")
      trace(1, 2 + handlers.size() + 2) {
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
          name "PublishedServerEventHandlerRpcHandler.handle"
          kind SpanKind.INTERNAL
          childOf span(spanIndex - 1)
        }
        span(spanIndex + 1) {
          name "JavaScriptBootstrapUI.connectClient"
          kind SpanKind.INTERNAL
          childOf span(spanIndex)
        }
      }
      handlers = getRequestHandlers("JavaScriptBootstrapHandler")
      trace(2, 2 + handlers.size()) {
        serverSpan(it, 0, getContextPath())
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
      def count = VAADIN_17 ? 5 : 4
      for (i in 0..count) {
        trace(3 + i, 1) {
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
