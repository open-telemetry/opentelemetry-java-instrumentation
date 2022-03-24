/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
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

  private final Instrumenter<Request, Response> instrumenter;
  private final ContextPropagators propagators;

  OkHttpTelemetry(Instrumenter<Request, Response> instrumenter, ContextPropagators propagators) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
  }

  /**
   * Returns a new {@link Interceptor} that can be used with methods like {@link
   * okhttp3.OkHttpClient.Builder#addInterceptor(Interceptor)}.
   *
   * <p>Important: asynchronous calls using {@link okhttp3.Call.Factory#enqueue(Callback)} will
   * *not* work correctly using just this interceptor.
   *
   * <p>It is strongly recommended that you use the {@link #newCallFactory(OkHttpClient)} method to
   * decorate your {@link OkHttpClient}, rather than using this method directly.
   *
   * @deprecated Please use the {@link #newCallFactory(OkHttpClient)} method instead.
   */
  @Deprecated
  public Interceptor newInterceptor() {
    return new TracingInterceptor(instrumenter, propagators);
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
    OkHttpClient tracingClient = baseClient.newBuilder().addInterceptor(newInterceptor()).build();
    return new TracingCallFactory(tracingClient);
  }
}
