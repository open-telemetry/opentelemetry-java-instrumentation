/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_12;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.okhttp.v3_12.internal.ContextPropagationInterceptor;
import io.opentelemetry.instrumentation.okhttp.v3_12.internal.ErrorCapturingInterceptor;
import io.opentelemetry.instrumentation.okhttp.v3_12.internal.TracingEventListener;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/**
 * Entrypoint for instrumenting OkHttp clients.
 *
 * <p>The client span lifecycle is driven by an okhttp {@link okhttp3.EventListener}. When network
 * timing capture is enabled (see {@link OkHttpTelemetryBuilder#setCaptureNetworkTimings(boolean)}),
 * the per-phase timings — including the response body read, which completes after the response
 * headers are received — are recorded as attributes on the client span.
 */
public final class OkHttpTelemetry {
  private final Instrumenter<Call, Response> instrumenter;
  private final ContextPropagators propagators;
  private final boolean captureNetworkTimings;

  /** Returns a new instance configured with the given {@link OpenTelemetry} instance. */
  public static OkHttpTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a builder configured with the given {@link OpenTelemetry} instance. */
  public static OkHttpTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new OkHttpTelemetryBuilder(openTelemetry);
  }

  OkHttpTelemetry(
      Instrumenter<Call, Response> instrumenter,
      ContextPropagators propagators,
      boolean captureNetworkTimings) {
    this.instrumenter = instrumenter;
    this.propagators = propagators;
    this.captureNetworkTimings = captureNetworkTimings;
  }

  /**
   * Returns an instrumented {@link okhttp3.Call.Factory} wrapping the provided client.
   *
   * <p>Supports both {@linkplain Call#execute() synchronous} and {@linkplain Call#enqueue(Callback)
   * asynchronous} calls with proper context propagation. Any {@link okhttp3.EventListener.Factory}
   * already configured on the client is preserved.
   *
   * @param baseClient the OkHttpClient to wrap
   * @return an instrumented Call.Factory
   */
  public Call.Factory createCallFactory(OkHttpClient baseClient) {
    OkHttpClient.Builder builder = baseClient.newBuilder();
    // capture call-level failures (e.g. too many redirects) that okhttp does not report through the
    // event listener, so they can be recorded on the span when it ends
    builder.interceptors().add(0, new ErrorCapturingInterceptor());
    // inject the span context, created by TracingEventListener#callStart, into outgoing requests
    builder.networkInterceptors().add(0, new ContextPropagationInterceptor(propagators));
    // drive the client span lifecycle from the event listener, preserving any user listener
    builder.eventListenerFactory(
        new TracingEventListener.Factory(instrumenter, baseClient.eventListenerFactory()));
    OkHttpClient tracingClient = builder.build();
    return new TracingCallFactory(tracingClient, captureNetworkTimings);
  }
}
