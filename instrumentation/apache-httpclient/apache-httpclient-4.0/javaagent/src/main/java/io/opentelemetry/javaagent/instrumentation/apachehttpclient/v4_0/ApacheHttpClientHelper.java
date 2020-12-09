/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientTracer.tracer;

import io.opentelemetry.context.Context;
import org.apache.http.HttpResponse;

public class ApacheHttpClientHelper {

  public static void doMethodExit(Context context, Object result, Throwable throwable) {
    if (result instanceof HttpResponse) {
      tracer()
          .onResponse(
              io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.spanFromContext(
                  context),
              (HttpResponse) result);
    } // else they probably provided a ResponseHandler

    if (throwable != null) {
      tracer().endExceptionally(context, throwable);
    } else {
      tracer().end(context);
    }
  }
}
