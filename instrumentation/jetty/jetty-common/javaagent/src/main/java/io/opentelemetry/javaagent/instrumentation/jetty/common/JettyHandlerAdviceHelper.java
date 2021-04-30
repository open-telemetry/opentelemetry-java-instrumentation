/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;
import io.opentelemetry.instrumentation.servlet.TagSettingAsyncListener;
import java.util.concurrent.atomic.AtomicBoolean;

public class JettyHandlerAdviceHelper {
  /** Shared method exit implementation for Jetty handler advices. */
  public static <REQUEST, RESPONSE> void stopSpan(
      ServletHttpServerTracer<REQUEST, RESPONSE> tracer,
      REQUEST request,
      RESPONSE response,
      Throwable throwable,
      Context context,
      Scope scope) {
    if (scope == null) {
      return;
    }
    scope.close();

    if (context == null) {
      // an existing span was found
      return;
    }

    tracer.setPrincipal(context, request);

    // throwable is read-only, copy it to a new local that can be modified
    Throwable exception = throwable;
    if (exception == null) {
      // on jetty versions before 9.4 exceptions from servlet don't propagate to this method
      // check from request whether a throwable has been stored there
      exception = tracer.errorException(request);
    }
    if (exception != null) {
      tracer.endExceptionally(context, exception, response);
      return;
    }

    AtomicBoolean responseHandled = new AtomicBoolean(false);
    ServletAccessor<REQUEST, RESPONSE> servletAccessor = tracer.getServletAccessor();

    // In case of async servlets wait for the actual response to be ready
    if (servletAccessor.isRequestAsyncStarted(request)) {
      try {
        servletAccessor.addRequestAsyncListener(
            request, new TagSettingAsyncListener<>(tracer, responseHandled, context));
      } catch (IllegalStateException e) {
        // org.eclipse.jetty.server.Request may throw an exception here if request became
        // finished after check above. We just ignore that exception and move on.
      }
    }

    // Check again in case the request finished before adding the listener.
    if (!servletAccessor.isRequestAsyncStarted(request)
        && responseHandled.compareAndSet(false, true)) {
      tracer.end(context, response);
    }
  }
}
