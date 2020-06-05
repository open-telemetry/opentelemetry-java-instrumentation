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

import static io.opentelemetry.auto.instrumentation.servlet.v3_0.Servlet3HttpServerTracer.TRACER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;

import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class Servlet3Advice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This final Object servlet,
      @Advice.Origin final Method method,
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    if (!(request instanceof HttpServletRequest)) {
      return;
    }

    final HttpServletRequest httpServletRequest = (HttpServletRequest) request;
    final Span existingSpan = TRACER.getAttachedSpan(httpServletRequest);
    if (existingSpan != null) {
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
      final boolean spanContextWasLost = !sameTrace(TRACER.getCurrentSpan(), existingSpan);
      if (spanContextWasLost) {
        // Put span from request attribute into current context.
        // We did not create a new span here, so return null instead
        scope = currentContextWith(existingSpan);
      }
      // We are inside nested servlet/filter, don't create new span
      return;
    }

    // For use by HttpServletResponseInstrumentation:
    InstrumentationContext.get(HttpServletResponse.class, HttpServletRequest.class)
        .put((HttpServletResponse) response, httpServletRequest);

    span = TRACER.startSpan(httpServletRequest, method, servlet.getClass().getName());
    scope = TRACER.newScope(span);
  }

  public static boolean sameTrace(Span oneSpan, Span otherSpan) {
    return oneSpan.getContext().getTraceId().equals(otherSpan.getContext().getTraceId());
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response,
      @Advice.Thrown final Throwable throwable,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    if (scope == null) {
      return;
    }

    if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
      TRACER.setPrincipal((HttpServletRequest) request);

      if (throwable != null) {
        TRACER.endExceptionally(
            span, scope, throwable, ((HttpServletResponse) response).getStatus());
        return;
      }

      // Usually Tracer takes care of this checks and of closing scopes.
      // But in case of async response processing we have to handle scope in this thread,
      // not in some arbitrary thread that may later take care of actual response.
      if (span == null) {
        scope.close();
        return;
      }

      final AtomicBoolean responseHandled = new AtomicBoolean(false);

      // In case of async servlets wait for the actual response to be ready
      if (request.isAsyncStarted()) {
        try {
          request.getAsyncContext().addListener(new TagSettingAsyncListener(responseHandled, span));
        } catch (final IllegalStateException e) {
          // org.eclipse.jetty.server.Request may throw an exception here if request became
          // finished after check above. We just ignore that exception and move on.
        }
      }

      // Check again in case the request finished before adding the listener.
      if (!request.isAsyncStarted() && responseHandled.compareAndSet(false, true)) {
        TRACER.end(span, ((HttpServletResponse) response).getStatus());
      }
    }
  }
}
