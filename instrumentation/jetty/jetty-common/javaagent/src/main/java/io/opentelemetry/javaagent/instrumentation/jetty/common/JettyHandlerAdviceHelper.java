/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jetty.common;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.servlet.ServletHttpServerTracer;
import io.opentelemetry.javaagent.instrumentation.servlet.common.service.ServletAndFilterAdviceHelper;

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

    if (ServletAndFilterAdviceHelper.mustEndOnHandlerMethodExit(tracer, request)) {
      tracer.end(context, response);
    }
  }
}
