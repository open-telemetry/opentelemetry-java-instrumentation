/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.v5_0.response;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;

public class HttpServletResponseAdviceHelper {
  public static void stopSpan(
      BaseTracer tracer,
      Class<?> responseClass,
      Throwable throwable,
      Context context,
      Scope scope,
      CallDepth callDepth) {
    if (callDepth.decrementAndGet() == 0 && context != null) {
      CallDepthThreadLocalMap.reset(responseClass);

      scope.close();

      if (throwable != null) {
        tracer.endExceptionally(context, throwable);
      } else {
        tracer.end(context);
      }
    }
  }
}
