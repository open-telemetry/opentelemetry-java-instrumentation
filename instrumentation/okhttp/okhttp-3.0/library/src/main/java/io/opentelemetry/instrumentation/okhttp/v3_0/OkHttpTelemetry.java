/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.ConnectionErrorSpanInterceptor;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.TracingInterceptor;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/** Entrypoint for instrumenting OkHttp clients. */
public final class OkHttpTelemetry {

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static OkHttpTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static OkHttpTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new OkHttpTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<Interceptor.Chain, Response> instrumenter;
  private final ContextPropagators propagators;

  OkHttpTelemetry(
      Instrumenter<Interceptor.Chain, Response> instrumenter, ContextPropagators propagators) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
  }

  /**
   * Returns an instrumented {@link okhttp3.Call.Factory} wrapping the provided client.
   *
   * <p>Supports both {@linkplain Call#execute() synchronous} and {@linkplain Call#enqueue(Callback)
   * asynchronous} calls with proper context propagation.
   *
   * @param baseClient the OkHttpClient to wrap
   * @return an instrumented Call.Factory
   */
  public Call.Factory createCallFactory(OkHttpClient baseClient) {
    OkHttpClient.Builder builder = baseClient.newBuilder();
    // add our interceptors before other interceptors
    builder.interceptors().add(0, new ContextInterceptor());
    builder.interceptors().add(1, new ConnectionErrorSpanInterceptor(instrumenter));
    builder.networkInterceptors().add(0, new TracingInterceptor(instrumenter, propagators));
    OkHttpClient tracingClient = builder.build();
    return new TracingCallFactory(tracingClient);
  }
}
