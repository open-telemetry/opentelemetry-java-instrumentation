/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.semconv.http.HttpClientRequestResendCount;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.ConnectionErrorSpanInterceptor;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.TracingInterceptor;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpClientInstrumenterBuilder;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/** Holder of singleton interceptors for adding to instrumented clients. */
public final class OkHttp3Singletons {

  private static final Instrumenter<Request, Response> INSTRUMENTER =
      JavaagentHttpClientInstrumenterBuilder.create(
          OkHttpTelemetry.builder(GlobalOpenTelemetry.get()));

  public static final Interceptor CONTEXT_INTERCEPTOR =
      chain -> {
        try (Scope ignored =
            HttpClientRequestResendCount.initialize(Context.current()).makeCurrent()) {
          return chain.proceed(chain.request());
        }
      };

  public static final Interceptor CONNECTION_ERROR_INTERCEPTOR =
      new ConnectionErrorSpanInterceptor(INSTRUMENTER);

  public static final Interceptor TRACING_INTERCEPTOR =
      new TracingInterceptor(INSTRUMENTER, GlobalOpenTelemetry.getPropagators());

  private OkHttp3Singletons() {}
}
