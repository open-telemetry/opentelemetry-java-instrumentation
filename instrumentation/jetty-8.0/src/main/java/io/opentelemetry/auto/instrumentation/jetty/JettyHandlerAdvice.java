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

package io.opentelemetry.auto.instrumentation.jetty;

import static io.opentelemetry.auto.instrumentation.jetty.JettyHttpServerTracer.TRACER;

import io.grpc.Context;
import io.opentelemetry.auto.instrumentation.servlet.v3_0.CountingHttpServletRequest;
import io.opentelemetry.auto.instrumentation.servlet.v3_0.CountingHttpServletResponse;
import io.opentelemetry.auto.instrumentation.servlet.v3_0.TagSettingAsyncListener;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class JettyHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Origin final Method method,
      @Advice.This final Object source,
      @Advice.Argument(value = 2, readOnly = false) HttpServletRequest request,
      @Advice.Argument(value = 3, readOnly = false) HttpServletResponse response,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {

    Context attachedContext = TRACER.getServerContext(request);
    if (attachedContext != null) {
      // We are inside nested handler, don't create new span
      return;
    }

    span = TRACER.startSpan(request, request, method);
    scope = TRACER.startScope(span, request);

    if (!(response instanceof CountingHttpServletResponse)) {
      response = new CountingHttpServletResponse(response);
      request = new CountingHttpServletRequest(request, response);
    }
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(2) final HttpServletRequest request,
      @Advice.Argument(3) final HttpServletResponse response,
      @Advice.Thrown final Throwable throwable,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {
    if (scope == null) {
      return;
    }
    scope.close();

    if (span == null) {
      // an existing span was found
      return;
    }

    TRACER.setPrincipal(span, request);

    if (throwable != null) {
      TRACER.endExceptionally(span, throwable, response.getStatus());
      return;
    }

    AtomicBoolean responseHandled = new AtomicBoolean(false);

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
      contentLengthHelper(span, response);
      TRACER.end(span, response.getStatus());
    }
  }

  public static void contentLengthHelper(Span span, ServletResponse response) {
    if (response instanceof CountingHttpServletResponse) {
      TRACER.setContentLength(span, ((CountingHttpServletResponse) response).getContentLength());
    }
  }
}
