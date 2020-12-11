/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import static io.opentelemetry.javaagent.instrumentation.httpclient.JdkHttpClientTracer.tracer;

import io.opentelemetry.context.Context;
import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public class ResponseConsumer implements BiConsumer<HttpResponse<?>, Throwable> {
  private final Context context;

  public ResponseConsumer(Context context) {
    this.context = context;
  }

  @Override
  public void accept(HttpResponse<?> httpResponse, Throwable throwable) {
    tracer().endMaybeExceptionally(context, httpResponse, throwable);
  }
}
