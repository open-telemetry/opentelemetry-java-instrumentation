/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.ApacheHttpClientInstrumenters.instrumenter;

import io.opentelemetry.context.Context;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

public final class ApacheHttpClientHelper {

  public static void doMethodExit(
      Context context, HttpUriRequest request, Object result, Throwable throwable) {
    if (throwable != null) {
      instrumenter().end(context, request, null, throwable);
    } else if (result instanceof HttpResponse) {
      instrumenter().end(context, request, (HttpResponse) result, null);
    } else {
      // ended in WrappingStatusSettingResponseHandler
    }
  }
}
