/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v4_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/** Entrypoint for tracing OkHttp clients. */
public final class OkHttpTracing {

  /** Returns a new {@link OkHttpTracing} configured with the given {@link OpenTelemetry}. */
  public static OkHttpTracing create(OpenTelemetry openTelemetry) {
    return newBuilder(openTelemetry).build();
  }

  /** Returns a new {@link OkHttpTracingBuilder} configured with the given {@link OpenTelemetry}. */
  public static OkHttpTracingBuilder newBuilder(OpenTelemetry openTelemetry) {
    return new OkHttpTracingBuilder(openTelemetry);
  }

  private final Instrumenter<Request, Response> instrumenter;
  private final ContextPropagators propagators;

  OkHttpTracing(Instrumenter<Request, Response> instrumenter, ContextPropagators propagators) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
  }

  /**
   * Returns a new {@link Interceptor} that can be used with methods like {@link
   * okhttp3.OkHttpClient.Builder#addInterceptor(Interceptor)}. Note that asynchronous calls using
   * {@link okhttp3.Call.Factory#enqueue(Callback)} will not work correctly using just this
   * interceptor.
   * <p>
   * It is strongly recommended that you use the {@link #newCallFactory(OkHttpClient)}
   * method to decorate your {@link OkHttpClient}, rather than using this method directly.
   */
  public Interceptor newInterceptor() {
    return new TracingInterceptor(instrumenter, propagators);
  }

  /**
   * Construct a new {@link okhttp3.Call.Factory} using the provided {@link OkHttpClient} instance.
   * Using this method should result in proper propagation and span parenting, both with synchronous ( {@link Call#execute()} )
   * and asynchronous ( {@link Call#enqueue(Callback)} usages.
   *
   * @param baseClient An instance of OkHttpClient configured as desired.
   * @return a {@link okhttp3.Call.Factory} for creating new {@link Call} instances.
   */
  public Call.Factory newCallFactory(OkHttpClient baseClient) {
    OkHttpClient tracingClient = baseClient.newBuilder()
        .addInterceptor(newInterceptor())
        .build();
    return new TracingCallFactory(tracingClient);
  }

}
