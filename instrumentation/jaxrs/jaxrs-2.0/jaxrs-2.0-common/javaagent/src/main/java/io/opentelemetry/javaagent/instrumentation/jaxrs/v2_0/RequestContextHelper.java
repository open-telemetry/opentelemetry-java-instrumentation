/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0;

import static io.opentelemetry.javaagent.instrumentation.jaxrs.v2_0.JaxRsAnnotationsTracer.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;

public final class RequestContextHelper {
  public static Span createOrUpdateAbortSpan(
      ContainerRequestContext requestContext, Class<?> resourceClass, Method method) {

    if (method != null && resourceClass != null) {
      requestContext.setProperty(JaxRsAnnotationsTracer.ABORT_HANDLED, true);
      Context context = Java8BytecodeBridge.currentContext();
      Span serverSpan = BaseTracer.getCurrentServerSpan(context);
      Span currentSpan = Java8BytecodeBridge.spanFromContext(context);

      // if there's no current span or it's the same as the server (servlet) span we need to start
      // a JAX-RS one
      // in other case, DefaultRequestContextInstrumentation must have already run so it's enough
      // to just update the names
      if (currentSpan == null || currentSpan == serverSpan) {
        return tracer().startSpan(resourceClass, method);
      } else {
        tracer().updateSpanNames(context, currentSpan, serverSpan, resourceClass, method);
      }
    }
    return null;
  }

  public static void closeSpanAndScope(Span span, Scope scope, Throwable throwable) {
    if (span == null || scope == null) {
      return;
    }

    if (throwable != null) {
      tracer().endExceptionally(span, throwable);
    } else {
      tracer().end(span);
    }

    scope.close();
  }

  private RequestContextHelper() {}
}
