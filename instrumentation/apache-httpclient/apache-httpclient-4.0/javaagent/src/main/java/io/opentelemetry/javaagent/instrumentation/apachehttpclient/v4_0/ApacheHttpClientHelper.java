/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientTracer.tracer;

import io.opentelemetry.instrumentation.api.tracer.Operation;
import org.apache.http.HttpResponse;

public class ApacheHttpClientHelper {

  public static void endOperation(Operation operation, Object result, Throwable throwable) {
    if (throwable != null) {
      tracer().endExceptionally(operation, throwable);
    } else if (result instanceof HttpResponse) {
      tracer().end(operation, (HttpResponse) result);
    } else {
      // ResponseHandler was probably provided
      tracer().end(operation, null);
    }
  }
}
