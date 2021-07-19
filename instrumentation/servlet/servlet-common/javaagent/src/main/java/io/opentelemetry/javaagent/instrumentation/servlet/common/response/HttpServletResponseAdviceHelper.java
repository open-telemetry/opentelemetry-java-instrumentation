/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.response;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.BaseTracer;

public class HttpServletResponseAdviceHelper {
  public static void stopSpan(
      BaseTracer tracer, Throwable throwable, Context context, Scope scope) {
    if (context != null) {
      scope.close();

      if (throwable != null) {
        tracer.endExceptionally(context, throwable);
      } else {
        tracer.end(context);
      }
    }
  }
}
