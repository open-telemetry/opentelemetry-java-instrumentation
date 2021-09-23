/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.service;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;

@Deprecated
public class ServletAndFilterAdviceHelper {
  public static <REQUEST, RESPONSE> void stopSpan(
      ServletHttpServerTracer<REQUEST, RESPONSE> tracer,
      REQUEST request,
      RESPONSE response,
      Throwable throwable,
      boolean topLevel,
      Context context,
      Scope scope) {

    if (scope != null) {
      scope.close();
    }

    if (context == null && topLevel) {
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

    if (mustEndOnHandlerMethodExit(tracer, request)) {
      tracer.end(context, response);
    }
  }

  /**
   * Helper method to determine whether the appserver handler/servlet service/servlet filter method
   * that started a span must also end it, even if no error was detected. Extracted as a separate
   * method to avoid duplicating the comments on the logic behind this choice.
   */
  public static <REQUEST, RESPONSE> boolean mustEndOnHandlerMethodExit(
      ServletHttpServerTracer<REQUEST, RESPONSE> tracer, REQUEST request) {

    if (tracer.isAsyncListenerAttached(request)) {
      // This request is handled asynchronously and startAsync instrumentation has already attached
      // the listener.
      return false;
    }

    // This means that startAsync was not called (assuming startAsync instrumentation works
    // correctly on this servlet engine), therefore the request was handled synchronously, and
    // handler method end must also end the span.
    return true;
  }
}
