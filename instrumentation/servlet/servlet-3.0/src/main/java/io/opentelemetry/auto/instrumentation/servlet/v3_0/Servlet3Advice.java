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
import static io.opentelemetry.context.ContextUtils.withScopedContext;

import io.grpc.Context;
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

    if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
      return;
    }

    final HttpServletRequest httpServletRequest = (HttpServletRequest) request;

    Context attachedContext = TRACER.getAttachedContext(httpServletRequest);
    if (attachedContext != null) {
      if (TRACER.needsRescoping(attachedContext)) {
        scope = withScopedContext(attachedContext);
      }
      // We are inside nested servlet/filter, don't create new span
      return;
    }

    // For use by HttpServletResponseInstrumentation:
    InstrumentationContext.get(HttpServletResponse.class, Boolean.class)
        .put((HttpServletResponse) response, true);

    span = TRACER.startSpan(httpServletRequest, method, servlet.getClass().getName());
    scope = TRACER.startScope(span, httpServletRequest);
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
    scope.close();

    if (span == null) {
      // an existing span was found
      return;
    }

    TRACER.setPrincipal(span, (HttpServletRequest) request);

    if (throwable != null) {
      TRACER.endExceptionally(span, throwable, ((HttpServletResponse) response).getStatus());
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
