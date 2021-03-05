/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxRsAnnotationsTracer.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;

public final class RequestContextHelper {
  public static Context createOrUpdateAbortSpan(
      ContainerRequestContext requestContext, Class<?> resourceClass, Method method) {

    if (method != null && resourceClass != null) {
      requestContext.setProperty(JaxRsAnnotationsTracer.ABORT_HANDLED, true);
      Context context = Java8BytecodeBridge.currentContext();
      Span serverSpan = ServerSpan.fromContextOrNull(context);
      Span currentSpan = Java8BytecodeBridge.spanFromContext(context);

      // if there's no current span or it's the same as the server (servlet) span we need to start
      // a JAX-RS one
      // in other case, DefaultRequestContextInstrumentation must have already run so it's enough
      // to just update the names
      if (currentSpan == null || currentSpan == serverSpan) {
        return tracer().startSpan(context, resourceClass, method);
      } else {
        tracer().updateSpanNames(context, currentSpan, serverSpan, resourceClass, method);
      }
    }
    return null;
  }

  public static void closeSpanAndScope(Context context, Scope scope, Throwable throwable) {
    if (context == null || scope == null) {
      return;
    }

    if (throwable != null) {
      tracer().endExceptionally(context, throwable);
    } else {
      tracer().end(context);
    }

    scope.close();
  }

  private RequestContextHelper() {}
}
