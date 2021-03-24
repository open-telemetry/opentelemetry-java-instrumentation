/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.vaadin

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan

import com.vaadin.flow.server.Version

abstract class AbstractVaadin16Test extends AbstractVaadinTest {
  static final boolean VAADIN_17 = Version.majorVersion >= 4
  static final boolean VAADIN_19 = Version.majorVersion >= 6

  @Override
  List<String> getRequestHandlers() {
    List<String> handlers = [
      "PushRequestHandler"
    ]
    if (VAADIN_19) {
      handlers.addAll("WebComponentBootstrapHandler", "WebComponentProvider", "PwaHandler")
    }
    handlers.addAll([
      "StreamRequestHandler", "UnsupportedBrowserHandler", "UidlRequestHandler",
      "HeartbeatHandler", "SessionRequestHandler", "JavaScriptBootstrapHandler", "FaviconHandler",
      "DevModeHandler", "IndexHtmlRequestHandler"
    ])

    return handlers
  }

  @Override
  void assertFirstRequest() {
    assertTraces(VAADIN_17 ? 9 : 8) {
      def handlers = getRequestHandlers("IndexHtmlRequestHandler")
      trace(0, 3 + handlers.size()) {
        basicSpan(it, 0, "IndexHtmlRequestHandler.handleRequest", null)
        basicSpan(it, 1, "ApplicationDispatcher.forward", span(0))
        basicSpan(it, 2, "SpringVaadinServletService.handleRequest", span(1))
        int spanIndex = 3
        handlers.each { handler ->
          basicSpan(it, spanIndex++, handler + ".handleRequest", span(2))
        }
      }
      trace(1, 2) {
        basicSpan(it, 0, getContextPath() + "/*", null)
        basicSpan(it, 1, "ApplicationDispatcher.forward", span(0))
      }
      if (VAADIN_17) {
        trace(2, 2) {
          basicSpan(it, 0, getContextPath() + "/*", null)
          basicSpan(it, 1, "ApplicationDispatcher.forward", span(0))
        }
      }
      int traceIndex = VAADIN_17 ? 3 : 2
      handlers = getRequestHandlers("JavaScriptBootstrapHandler")
      trace(traceIndex, 3 + handlers.size()) {
        basicSpan(it, 0, getContextPath(), null)
        basicSpan(it, 1, "ApplicationDispatcher.forward", span(0))
        basicSpan(it, 2, "SpringVaadinServletService.handleRequest", span(1))
        int spanIndex = 3
        handlers.each { handler ->
          basicSpan(it, spanIndex++, handler + ".handleRequest", span(2))
        }
      }
      trace(traceIndex + 1, 2) {
        basicSpan(it, 0, getContextPath() + "/*", null)
        basicSpan(it, 1, "ApplicationDispatcher.forward", span(0))
      }
      trace(traceIndex + 2, 2) {
        basicSpan(it, 0, getContextPath() + "/*", null)
        basicSpan(it, 1, "ApplicationDispatcher.forward", span(0))
      }
      trace(traceIndex + 3, 2) {
        basicSpan(it, 0, getContextPath() + "/*", null)
        basicSpan(it, 1, "ApplicationDispatcher.forward", span(0))
      }
      trace(traceIndex + 4, 2) {
        basicSpan(it, 0, getContextPath() + "/*", null)
        basicSpan(it, 1, "ApplicationDispatcher.forward", span(0))
      }
      handlers = getRequestHandlers("UidlRequestHandler")
      trace(traceIndex + 5, 3 + handlers.size() + 2) {
        basicSpan(it, 0, getContextPath() + "/main", null)
        basicSpan(it, 1, "ApplicationDispatcher.forward", span(0))
        basicSpan(it, 2, "SpringVaadinServletService.handleRequest", span(1))

        int spanIndex = 3
        handlers.each { handler ->
          basicSpan(it, spanIndex++, handler + ".handleRequest", span(2))
        }

        basicSpan(it, spanIndex, "PublishedServerEventHandlerRpcHandler.handle", span(spanIndex - 1))
        basicSpan(it, spanIndex + 1, "JavaScriptBootstrapUI.connectClient", span(spanIndex))
      }
    }
  }

  @Override
  void assertButtonClick() {
    assertTraces(1) {
      def handlers = getRequestHandlers("UidlRequestHandler")
      trace(0, 3 + handlers.size() + 1) {
        basicSpan(it, 0, getContextPath() + "/main", null)
        basicSpan(it, 1, "ApplicationDispatcher.forward", span(0))
        basicSpan(it, 2, "SpringVaadinServletService.handleRequest", span(1))

        int spanIndex = 3
        handlers.each { handler ->
          basicSpan(it, spanIndex++, handler + ".handleRequest", span(2))
        }

        basicSpan(it, spanIndex, "EventRpcHandler.handle/click", span(spanIndex - 1))
      }
    }
  }
}
