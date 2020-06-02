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

import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
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
  public static SpanWithScope onEnter(
      @Advice.This final Object servlet,
      @Advice.Origin final Method method,
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response) {
    if (!(request instanceof HttpServletRequest)) {
      return null;
    }

    final HttpServletRequest httpServletRequest = (HttpServletRequest) request;

    // For use by HttpServletResponseInstrumentation:
    InstrumentationContext.get(HttpServletResponse.class, HttpServletRequest.class)
        .put((HttpServletResponse) response, httpServletRequest);

    return TRACER.startSpan(httpServletRequest, method, servlet.getClass().getName());
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(0) final ServletRequest request,
      @Advice.Argument(1) final ServletResponse response,
      @Advice.Enter final SpanWithScope spanWithScope,
      @Advice.Thrown final Throwable throwable) {
    if (spanWithScope == null) {
      return;
    }

    if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
      TRACER.setPrincipal((HttpServletRequest) request);

      if (throwable != null) {
        TRACER.endExceptionally(
            spanWithScope, throwable, ((HttpServletResponse) response).getStatus());
        return;
      }

      // Usually Tracer takes care of this checks and of closing scopes.
      // But in case of async response processing we have to handle scope in this thread,
      // not in some arbitrary thread that may later take care of actual response.
      Span span = spanWithScope.getSpan();
      if (span == null) {
        spanWithScope.closeScope();
        return;
      }

      final AtomicBoolean responseHandled = new AtomicBoolean(false);

      // In case of async servlets wait for the actual response to be ready
      if (request.isAsyncStarted()) {
        try {
          request
              .getAsyncContext()
              .addListener(new TagSettingAsyncListener(responseHandled, span, TRACER));
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
