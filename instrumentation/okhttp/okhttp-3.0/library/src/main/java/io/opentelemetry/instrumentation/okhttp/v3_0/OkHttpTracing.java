/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.api.OpenTelemetry;
import okhttp3.Interceptor;

/** Entrypoint for tracing OkHttp clients. */
public final class OkHttpTracing {

  /** Returns a new {@link OkHttpTracing} configured with the given {@link OpenTelemetry}. */
  public static OkHttpTracing create(OpenTelemetry openTelemetry) {
    return new OkHttpTracing(openTelemetry);
  }

  private final OkHttpClientTracer tracer;

  private OkHttpTracing(OpenTelemetry openTelemetry) {
    this.tracer = new OkHttpClientTracer(openTelemetry);
  }

  /**
   * Returns a new {@link Interceptor} that can be used with methods like {@link
   * okhttp3.OkHttpClient.Builder#addInterceptor(Interceptor)}.
   */
  public Interceptor newInterceptor() {
    return new TracingInterceptor(tracer);
  }
}
