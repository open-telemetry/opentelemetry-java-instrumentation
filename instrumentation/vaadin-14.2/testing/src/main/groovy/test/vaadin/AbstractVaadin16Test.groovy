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
      trace(0, 2 + handlers.size()) {
        serverSpan(it, 0, "IndexHtmlRequestHandler.handleRequest")
        basicSpan(it, 1, "SpringVaadinServletService.handleRequest", span(0))
        int spanIndex = 2
        handlers.each { handler ->
          basicSpan(it, spanIndex++, handler + ".handleRequest", span(1))
        }
      }
      // /xyz/VAADIN/build/vaadin-bundle-*.cache.js
      trace(1, 1) {
        serverSpan(it, 0, getContextPath() + "/*")
      }
      if (VAADIN_17) {
        // /xyz/VAADIN/build/vaadin-devmodeGizmo-*.cache.js
        trace(2, 1) {
          serverSpan(it, 0, getContextPath() + "/*")
        }
      }
      int traceIndex = VAADIN_17 ? 3 : 2
      handlers = getRequestHandlers("JavaScriptBootstrapHandler")
      trace(traceIndex, 2 + handlers.size()) {
        serverSpan(it, 0, getContextPath())
        basicSpan(it, 1, "SpringVaadinServletService.handleRequest", span(0))
        int spanIndex = 2
        handlers.each { handler ->
          basicSpan(it, spanIndex++, handler + ".handleRequest", span(1))
        }
      }
      // /xyz/VAADIN/build/vaadin-?-*.cache.js
      trace(traceIndex + 1, 1) {
        serverSpan(it, 0, getContextPath() + "/*")
      }
      // /xyz/VAADIN/build/vaadin-?-*.cache.js
      trace(traceIndex + 2, 1) {
        serverSpan(it, 0, getContextPath() + "/*")
      }
      // /xyz/VAADIN/build/vaadin-?-*.cache.js
      trace(traceIndex + 3, 1) {
        serverSpan(it, 0, getContextPath() + "/*")
      }
      // /xyz/VAADIN/build/vaadin-?-*.cache.js
      trace(traceIndex + 4, 1) {
        serverSpan(it, 0, getContextPath() + "/*")
      }
      handlers = getRequestHandlers("UidlRequestHandler")
      trace(traceIndex + 5, 2 + handlers.size() + 2) {
        serverSpan(it, 0, getContextPath() + "/main")
        basicSpan(it, 1, "SpringVaadinServletService.handleRequest", span(0))

        int spanIndex = 2
        handlers.each { handler ->
          basicSpan(it, spanIndex++, handler + ".handleRequest", span(1))
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
      trace(0, 2 + handlers.size() + 1) {
        serverSpan(it, 0, getContextPath() + "/main")
        basicSpan(it, 1, "SpringVaadinServletService.handleRequest", span(0))

        int spanIndex = 2
        handlers.each { handler ->
          basicSpan(it, spanIndex++, handler + ".handleRequest", span(1))
        }

        basicSpan(it, spanIndex, "EventRpcHandler.handle/click", span(spanIndex - 1))
      }
    }
  }
}
