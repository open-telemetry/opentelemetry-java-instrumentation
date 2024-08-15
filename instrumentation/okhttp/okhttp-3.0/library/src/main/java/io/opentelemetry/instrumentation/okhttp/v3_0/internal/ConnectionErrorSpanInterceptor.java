/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientRequestResendCount;
import java.io.IOException;
import java.time.Instant;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ConnectionErrorSpanInterceptor implements Interceptor {

  private final Instrumenter<Chain, Response> instrumenter;

  public ConnectionErrorSpanInterceptor(Instrumenter<Chain, Response> instrumenter) {
    this.instrumenter = instrumenter;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    Context parentContext = Context.current();
    Response response = null;
    Throwable error = null;
    Instant startTime = Instant.now();
    try {
      response = chain.proceed(request);
      return response;
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      // only create a span when there wasn't any HTTP request
      if (HttpClientRequestResendCount.get(parentContext) == 0) {
        if (instrumenter.shouldStart(parentContext, chain)) {
          InstrumenterUtil.startAndEnd(
              instrumenter, parentContext, chain, response, error, startTime, Instant.now());
        }
      }
    }
  }
}
