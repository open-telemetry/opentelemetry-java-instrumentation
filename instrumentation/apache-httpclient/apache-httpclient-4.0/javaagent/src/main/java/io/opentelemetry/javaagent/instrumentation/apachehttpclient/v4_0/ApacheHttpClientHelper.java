/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientTracer.tracer;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.apache.http.HttpResponse;

public class ApacheHttpClientHelper {

  public static void doMethodExit(
      Context context, Scope scope, Object result, Throwable throwable) {
    try {
      if (result instanceof HttpResponse) {
        tracer().onResponse(Span.fromContext(context), (HttpResponse) result);
      } // else they probably provided a ResponseHandler
      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      } else {
        tracer().end(context);
      }
    } finally {
      scope.close();
    }
  }
}
