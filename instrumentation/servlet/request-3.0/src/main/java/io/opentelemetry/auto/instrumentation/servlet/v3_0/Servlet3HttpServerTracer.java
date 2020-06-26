/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.auto.instrumentation.servlet.v3_0;

import io.opentelemetry.auto.instrumentation.servlet.ServletHttpServerTracer;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import java.io.IOException;
import javax.servlet.Filter;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Servlet3HttpServerTracer extends ServletHttpServerTracer {
  public static final Servlet3HttpServerTracer TRACER = new Servlet3HttpServerTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.servlet-3.0";
  }

  @Override
  protected Integer peerPort(HttpServletRequest request) {
    return request.getRemotePort();
  }

  public void onTimeout(Span span, long timeout) {
    span.setStatus(Status.DEADLINE_EXCEEDED);
    span.setAttribute("timeout", timeout);
    span.end();
  }

  /*
  Given request already has a span associated with it.
  As there should not be nested spans of kind SERVER, we should NOT create a new span here.

  But it may happen that there is no span in current Context or it is from a different trace.
  E.g. in case of async servlet request processing we create span for incoming request in one thread,
  but actual request continues processing happens in another thread.
  Depending on servlet container implementation, this processing may again arrive into this method.
  E.g. Jetty handles async requests in a way that calls HttpServlet.service method twice.

  In this case we have to put the span from the request into current context before continuing.
  */
  public boolean needsRescoping(Span attachedSpan) {
    return !sameTrace(tracer.getCurrentSpan(), attachedSpan);
  }

  @Override
  public void onRequest(Span span, HttpServletRequest request) {
    super.onRequest(span, request);
    onContext(span, request, request.getServletContext());
  }

  /**
   * This method executes the filter created by
   * io.opentelemetry.auto.instrumentation.springwebmvc.DispatcherServletInstrumentation$HandlerMappingAdvice.
   * This was easier and less "hacky" than other ways to add the filter to the front of the filter
   * chain.
   */
  private void onContext(
      final Span span, final HttpServletRequest request, final ServletContext context) {
    if (context == null) {
      // some frameworks (jetty) may return a null context.
      return;
    }
    final Object attribute = context.getAttribute("io.opentelemetry.auto.dispatcher-filter");
    if (attribute instanceof Filter) {
      final Object priorAttr = request.getAttribute(SPAN_ATTRIBUTE);
      request.setAttribute(SPAN_ATTRIBUTE, span);
      try {
        ((Filter) attribute).doFilter(request, null, null);
      } catch (final IOException | ServletException e) {
        log.debug("Exception unexpectedly thrown by filter", e);
      } finally {
        request.setAttribute(SPAN_ATTRIBUTE, priorAttr);
      }
    }
  }

  private static boolean sameTrace(final Span oneSpan, final Span otherSpan) {
    return oneSpan.getContext().getTraceId().equals(otherSpan.getContext().getTraceId());
  }
}
