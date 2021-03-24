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
      trace(0, 3 + handlers.size()) {
        basicSpan(it, 0, getContextPath() + "/main", null)
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
      trace(2, 2) {
        basicSpan(it, 0, getContextPath() + "/*", null)
        basicSpan(it, 1, "ApplicationDispatcher.forward", span(0))
      }
      trace(3, 2) {
        basicSpan(it, 0, getContextPath() + "/*", null)
        basicSpan(it, 1, "ApplicationDispatcher.forward", span(0))
      }
      if (VAADIN_14_4) {
        trace(4, 2) {
          basicSpan(it, 0, getContextPath() + "/*", null)
          basicSpan(it, 1, "ApplicationDispatcher.forward", span(0))
        }
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
