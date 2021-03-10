/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.tracer.net.NetPeerAttributes;
import java.util.Map;
import okhttp3.Interceptor;

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

  private final OkHttpClientTracer tracer;

  OkHttpTracing(OpenTelemetry openTelemetry, Map<String, String> peerServiceMapping) {
    this.tracer = new OkHttpClientTracer(openTelemetry, new NetPeerAttributes(peerServiceMapping));
  }

  /**
   * Returns a new {@link Interceptor} that can be used with methods like {@link
   * okhttp3.OkHttpClient.Builder#addInterceptor(Interceptor)}.
   */
  public Interceptor newInterceptor() {
    return new TracingInterceptor(tracer);
  }
}
