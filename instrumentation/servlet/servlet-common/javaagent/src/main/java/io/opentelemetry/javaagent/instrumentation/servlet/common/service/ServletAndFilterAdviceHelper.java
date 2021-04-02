/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.service;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.servlet.AppServerBridge;
import io.opentelemetry.instrumentation.servlet.ServletAccessor;
import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;
import io.opentelemetry.instrumentation.servlet.TagSettingAsyncListener;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.util.concurrent.atomic.AtomicBoolean;

public class ServletAndFilterAdviceHelper {
  public static <REQUEST, RESPONSE> void stopSpan(
      ServletHttpServerTracer<REQUEST, RESPONSE> tracer,
      REQUEST request,
      RESPONSE response,
      Throwable throwable,
      Context context,
      Scope scope) {
    int callDepth = CallDepthThreadLocalMap.decrementCallDepth(AppServerBridge.getCallDepthKey());

    if (scope != null) {
      scope.close();
    }

    if (context == null && callDepth == 0) {
      Context currentContext = Java8BytecodeBridge.currentContext();
      // Something else is managing the context, we're in the outermost level of Servlet
      // instrumentation and we have an uncaught throwable. Let's add it to the current span.
      if (throwable != null) {
        tracer.addUnwrappedThrowable(currentContext, throwable);
      }
      tracer.setPrincipal(currentContext, request);
    }

    if (scope == null || context == null) {
      return;
    }

    tracer.setPrincipal(context, request);
    if (throwable != null) {
      tracer.endExceptionally(context, throwable, response);
      return;
    }

    AtomicBoolean responseHandled = new AtomicBoolean(false);
    ServletAccessor<REQUEST, RESPONSE> accessor = tracer.getServletAccessor();

    // In case of async servlets wait for the actual response to be ready
    if (accessor.isRequestAsyncStarted(request)) {
      try {
        accessor.addRequestAsyncListener(
            request, new TagSettingAsyncListener<>(tracer, responseHandled, context));
      } catch (IllegalStateException e) {
        // org.eclipse.jetty.server.Request may throw an exception here if request became
        // finished after check above. We just ignore that exception and move on.
      }
    }

    // Check again in case the request finished before adding the listener.
    if (!accessor.isRequestAsyncStarted(request) && responseHandled.compareAndSet(false, true)) {
      tracer.end(context, response);
    }
  }
}
