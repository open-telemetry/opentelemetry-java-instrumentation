/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0;

import io.opentelemetry.api.OpenTelemetry;
import java.util.concurrent.ExecutorService;
import okhttp3.Callback;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;

/** Entrypoint for tracing OkHttp clients. */
public final class OkHttpTracing {

  /** Returns a new {@link OkHttpTracing} configured with the given {@link OpenTelemetry}. */
  public static OkHttpTracing create(OpenTelemetry openTelemetry) {
    return new OkHttpTracing(openTelemetry);
  }

  private final OkHttpClientTracer tracer;

  OkHttpTracing(OpenTelemetry openTelemetry) {
    this.tracer = new OkHttpClientTracer(openTelemetry);
  }

  /**
   * Returns a new {@link Interceptor} that can be used with methods like {@link
   * okhttp3.OkHttpClient.Builder#addInterceptor(Interceptor)}. Note that asynchronous calls using
   * {@link okhttp3.Call.Factory#enqueue(Callback)} will not work correctly unless you also decorate
   * the {@linkplain Dispatcher#executorService() dispatcher's executor service} with {@link
   * io.opentelemetry.context.Context#taskWrapping(ExecutorService)}. For example, if using the
   * default {@link Dispatcher}, you will need to configure {@link okhttp3.OkHttpClient.Builder}
   * something like
   *
   * <pre>{@code
   * new OkHttpClient.Builder()
   *   .dispatcher(new Dispatcher(Context.taskWrapping(new Dispatcher().executorService())))
   *   .addInterceptor(OkHttpTracing.create(openTelemetry).newInterceptor())
   *   ...
   * }</pre>
   */
  public Interceptor newInterceptor() {
    return new TracingInterceptor(tracer);
  }
}
