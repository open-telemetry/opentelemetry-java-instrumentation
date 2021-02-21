/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientTracer.tracer;

import io.opentelemetry.context.Context;
import org.apache.hc.core5.http.HttpResponse;

public class ApacheHttpClientHelper {

  public static void doMethodExit(Context context, Object result, Throwable throwable) {
    if (throwable != null) {
      tracer().endExceptionally(context, throwable);
    } else if (result instanceof HttpResponse) {
      tracer().end(context, (HttpResponse) result);
    } else {
      // ended in WrappingStatusSettingResponseHandler
    }
  }
}
