/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v3_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.okhttp.v3_0.OkHttpTracing;
import okhttp3.Interceptor;

/** Holder of singleton interceptors for adding to instrumented clients. */
public class OkHttp3Interceptors {

  public static final Interceptor TRACING_INTERCEPTOR =
      OkHttpTracing.create(GlobalOpenTelemetry.get()).newInterceptor();
}
