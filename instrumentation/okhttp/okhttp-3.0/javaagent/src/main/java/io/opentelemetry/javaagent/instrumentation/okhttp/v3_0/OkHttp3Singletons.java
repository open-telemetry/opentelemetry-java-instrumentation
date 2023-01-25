/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientResend;
import io.opentelemetry.instrumentation.api.instrumenter.net.PeerServiceAttributesExtractor;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTelemetry;
import io.opentelemetry.instrumentation.okhttp.v3_0.internal.OkHttpNetAttributesGetter;
import io.opentelemetry.javaagent.bootstrap.internal.CommonConfig;
import okhttp3.Interceptor;

/** Holder of singleton interceptors for adding to instrumented clients. */
public final class OkHttp3Singletons {

  @SuppressWarnings("deprecation") // we're still using the interceptor on its own for now
  public static final Interceptor TRACING_INTERCEPTOR =
      OkHttpTelemetry.builder(GlobalOpenTelemetry.get())
          .addAttributesExtractor(
              PeerServiceAttributesExtractor.create(
                  new OkHttpNetAttributesGetter(), CommonConfig.get().getPeerServiceMapping()))
          .setCapturedRequestHeaders(CommonConfig.get().getClientRequestHeaders())
          .setCapturedResponseHeaders(CommonConfig.get().getClientResponseHeaders())
          .build()
          .newInterceptor();

  public static final Interceptor CONTEXT_INTERCEPTOR =
      chain -> {
        try (Scope ignored = HttpClientResend.initialize(Context.current()).makeCurrent()) {
          return chain.proceed(chain.request());
        }
      };

  private OkHttp3Singletons() {}
}
