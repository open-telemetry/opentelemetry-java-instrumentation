/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.servlet.common.response;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.ClassAndMethod;

public class HttpServletResponseAdviceHelper {
  public static void stopSpan(
      Instrumenter<ClassAndMethod, Void> instrumenter,
      Throwable throwable,
      Context context,
      Scope scope,
      ClassAndMethod request) {
    if (scope != null) {
      scope.close();

      instrumenter.end(context, request, null, throwable);
    }
  }
}
