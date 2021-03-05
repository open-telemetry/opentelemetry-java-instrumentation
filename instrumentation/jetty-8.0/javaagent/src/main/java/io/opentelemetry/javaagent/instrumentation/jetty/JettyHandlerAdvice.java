/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty;

import static io.opentelemetry.javaagent.instrumentation.jetty.JettyHttpServerTracer.tracer;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.servlet.v3_0.TagSettingAsyncListener;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.bytebuddy.asm.Advice;

public class JettyHandlerAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.This Object source,
      @Advice.Argument(value = 2, readOnly = false) HttpServletRequest request,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    Context attachedContext = tracer().getServerContext(request);
    if (attachedContext != null) {
      // We are inside nested handler, don't create new span
      return;
    }

    context = tracer().startServerSpan(request);
    scope = context.makeCurrent();
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void stopSpan(
      @Advice.Argument(2) HttpServletRequest request,
      @Advice.Argument(3) HttpServletResponse response,
      @Advice.Thrown Throwable throwable,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    if (scope == null) {
      return;
    }
    scope.close();

    if (context == null) {
      // an existing span was found
      return;
    }

    tracer().setPrincipal(context, request);

    // throwable is read-only, copy it to a new local that can be modified
    Throwable exception = throwable;
    if (exception == null) {
      // on jetty versions before 9.4 exceptions from servlet don't propagate to this method
      // check from request whether a throwable has been stored there
      exception = (Throwable) request.getAttribute("javax.servlet.error.exception");
    }
    if (exception != null) {
      tracer().endExceptionally(context, exception, response);
      return;
    }

    AtomicBoolean responseHandled = new AtomicBoolean(false);

    // In case of async servlets wait for the actual response to be ready
    if (request.isAsyncStarted()) {
      try {
        request
            .getAsyncContext()
            .addListener(new TagSettingAsyncListener(responseHandled, context));
      } catch (IllegalStateException e) {
        // org.eclipse.jetty.server.Request may throw an exception here if request became
        // finished after check above. We just ignore that exception and move on.
      }
    }

    // Check again in case the request finished before adding the listener.
    if (!request.isAsyncStarted() && responseHandled.compareAndSet(false, true)) {
      tracer().end(context, response);
    }
  }
}
