/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test.vaadin


import com.vaadin.flow.server.Version
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.sdk.trace.data.SpanData

abstract class AbstractVaadin16Test extends AbstractVaadinTest {
  static final boolean VAADIN_17 = Version.majorVersion >= 4
  static final boolean VAADIN_19 = Version.majorVersion >= 6
  static final boolean VAADIN_21 = Version.majorVersion >= 8
  static final boolean VAADIN_22 = Version.majorVersion >= 9
  static final boolean VAADIN_23 = Version.majorVersion >= 23
  static final boolean VAADIN_23_2 = Version.majorVersion > 23 || (Version.majorVersion == 23 && Version.minorVersion >= 2)

  @Override
  List<String> getRequestHandlers() {
    List<String> handlers = []
    if (VAADIN_23_2) {
      handlers.add("ViteHandler")
    } else if (VAADIN_22) {
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
    def tracesCount
    if (VAADIN_23_2) {
      tracesCount = 12
    } else if (VAADIN_17) {
      tracesCount = 9
    } else {
      tracesCount = 8
    }
    assertTraces(tracesCount) {
      traces.sort(orderByRootSpanName("IndexHtmlRequestHandler.handleRequest",
        getContextPath() + "/main", getContextPath(), getContextPath() + "/", getContextPath() + "/*",
        getContextPath() + "/VAADIN/*"))

      def handlers = getRequestHandlers("IndexHtmlRequestHandler")
      trace(0, 2 + handlers.size()) {
        serverSpan(it, 0, "IndexHtmlRequestHandler.handleRequest")
        span(1) {
          name "SpringVaadinServletService.handleRequest"
          kind SpanKind.INTERNAL
          childOf span(0)
        }
        int spanIndex = 2
        sortHandlerSpans(spans, spanIndex, handlers)
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
        sortHandlerSpans(spans, spanIndex, handlers)
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
        sortHandlerSpans(spans, spanIndex, handlers)
        handlers.each { handler ->
          span(spanIndex++) {
            name handler + ".handleRequest"
            kind SpanKind.INTERNAL
            childOf span(1)
          }
        }
      }
      // following traces are for javascript files used on page
      def count = traces.size() - 4
      for (i in 0..count) {
        trace(3 + i, 1) {
          def spanName
          if (VAADIN_23_2) {
            spanName = i != 0 ? getContextPath() + "/*" : getContextPath() + "/"
          } else {
            spanName = VAADIN_23 && i != 0 ? getContextPath() + "/VAADIN/*" : getContextPath() + "/*"
          }
          serverSpan(it, 0, spanName)
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
        sortHandlerSpans(spans, spanIndex, handlers)
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

  static void sortHandlerSpans(List<SpanData> spans, int startIndex, List<String> handlers) {
    spans.subList(startIndex, startIndex + handlers.size()).sort({
      // strip .handleRequest from span name to get the handler name
      def handlerName = it.name.substring(0, it.name.indexOf('.'))
      return handlers.indexOf(handlerName)
    })
  }
}
