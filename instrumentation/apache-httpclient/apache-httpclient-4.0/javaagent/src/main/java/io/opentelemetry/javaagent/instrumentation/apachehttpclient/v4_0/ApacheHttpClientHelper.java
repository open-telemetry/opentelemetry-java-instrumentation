/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import io.opentelemetry.instrumentation.api.tracer.Operation;
import org.apache.http.HttpResponse;

public class ApacheHttpClientHelper {

  public static void endOperation(
      Operation<HttpResponse> operation, Object result, Throwable throwable) {
    if (throwable != null) {
      operation.endExceptionally(throwable);
    } else if (result instanceof HttpResponse) {
      operation.end((HttpResponse) result);
    } else {
      // ended in WrappingStatusSettingResponseHandler
    }
  }
}
