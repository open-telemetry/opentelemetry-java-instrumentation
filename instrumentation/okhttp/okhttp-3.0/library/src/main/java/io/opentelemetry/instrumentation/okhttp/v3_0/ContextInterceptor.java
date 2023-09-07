/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientResendCount;
import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

final class ContextInterceptor implements Interceptor {

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    Context parentContext = TracingCallFactory.getCallingContextForRequest(request);
    if (parentContext == null) {
      parentContext = Context.current();
    }
    // include the resend counter
    Context context = HttpClientResendCount.initialize(parentContext);
    try (Scope ignored = context.makeCurrent()) {
      return chain.proceed(request);
    }
  }
}
