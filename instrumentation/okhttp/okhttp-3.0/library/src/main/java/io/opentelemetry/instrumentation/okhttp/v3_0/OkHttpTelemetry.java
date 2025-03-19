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

  /** Returns a new {@link OkHttpTelemetry} configured with the given {@link OpenTelemetry}. */
  public static OkHttpTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /**
   * Returns a new {@link OkHttpTelemetryBuilder} configured with the given {@link OpenTelemetry}.
   */
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
   * Construct a new OpenTelemetry tracing-enabled {@link okhttp3.Call.Factory} using the provided
   * {@link OkHttpClient} instance.
   *
   * <p>Using this method will result in proper propagation and span parenting, for both {@linkplain
   * Call#execute() synchronous} and {@linkplain Call#enqueue(Callback) asynchronous} usages.
   *
   * @param baseClient An instance of OkHttpClient configured as desired.
   * @return a {@link Call.Factory} for creating new {@link Call} instances.
   */
  public Call.Factory newCallFactory(OkHttpClient baseClient) {
    OkHttpClient.Builder builder = baseClient.newBuilder();
    // add our interceptors before other interceptors
    builder.interceptors().add(0, new ContextInterceptor());
    builder.interceptors().add(1, new ConnectionErrorSpanInterceptor(instrumenter));
    builder.networkInterceptors().add(0, new TracingInterceptor(instrumenter, propagators));
    OkHttpClient tracingClient = builder.build();
    return new TracingCallFactory(tracingClient);
  }
}
