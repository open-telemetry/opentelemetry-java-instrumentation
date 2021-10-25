/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.response;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.lang.reflect.Method;

public class HttpServletResponseAdviceHelper {
  public static void stopSpan(
      Instrumenter<Method, Void> instrumenter,
      Throwable throwable,
      Context context,
      Scope scope,
      Method request) {
    if (scope != null) {
      scope.close();

      instrumenter.end(context, request, null, throwable);
    }
  }
}
