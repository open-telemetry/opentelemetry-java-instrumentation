/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty;

import static io.opentelemetry.javaagent.instrumentation.jetty.JettyHttpServerTracer.TRACER;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.TagSettingAsyncListener;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class JettyHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Origin Method method,
      @Advice.This Object source,
      @Advice.Argument(value = 2, readOnly = false) HttpServletRequest request,
      @Advice.Local("otelSpan") Span span,
      @Advice.Local("otelScope") Scope scope) {

    Context attachedContext = TRACER.getServerContext(request);
    if (attachedContext != null) {
      // We are inside nested handler, don't create new span
      return;
    }

    Context ctx = TRACER.startSpan(request, request, method);
    span = Java8BytecodeBridge.spanFromContext(ctx);
    scope = TRACER.startScope(span, request);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(2) HttpServletRequest request,
      @Advice.Argument(3) HttpServletResponse response,
      @Advice.Thrown Throwable throwable,
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
      TRACER.endExceptionally(span, throwable, response);
      return;
    }

    AtomicBoolean responseHandled = new AtomicBoolean(false);

    // In case of async servlets wait for the actual response to be ready
    if (request.isAsyncStarted()) {
      try {
        request.getAsyncContext().addListener(new TagSettingAsyncListener(responseHandled, span));
      } catch (IllegalStateException e) {
        // org.eclipse.jetty.server.Request may throw an exception here if request became
        // finished after check above. We just ignore that exception and move on.
      }
    }

    // Check again in case the request finished before adding the listener.
    if (!request.isAsyncStarted() && responseHandled.compareAndSet(false, true)) {
      TRACER.end(span, response);
    }
  }
}
