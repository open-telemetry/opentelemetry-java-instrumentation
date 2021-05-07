/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.vaadin

import static io.opentelemetry.instrumentation.test.utils.TraceUtils.basicSpan

import com.vaadin.flow.server.Version

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
      def handlers = getRequestHandlers("BootstrapHandler")
      trace(0, 2 + handlers.size()) {
        serverSpan(it, 0, getContextPath() + "/main")
        basicSpan(it, 1, "SpringVaadinServletService.handleRequest", span(0))

        int spanIndex = 2
        handlers.each { handler ->
          basicSpan(it, spanIndex++, handler + ".handleRequest", span(1))
        }
      }
      // following traces are for javascript files used on page
      trace(1, 1) {
        serverSpan(it, 0, getContextPath() + "/*")
      }
      trace(2, 1) {
        serverSpan(it, 0, getContextPath() + "/*")
      }
      trace(3, 1) {
        serverSpan(it, 0, getContextPath() + "/*")
      }
      if (VAADIN_14_4) {
        trace(4, 1) {
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
