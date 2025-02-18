/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javahttpclient.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class ResponseConsumer implements BiConsumer<HttpResponse<?>, Throwable> {
  private final Instrumenter<HttpRequest, HttpResponse<?>> instrumenter;
  private final Context context;
  private final HttpRequest httpRequest;

  public ResponseConsumer(
      Instrumenter<HttpRequest, HttpResponse<?>> instrumenter,
      Context context,
      HttpRequest httpRequest) {
    this.instrumenter = instrumenter;
    this.context = context;
    this.httpRequest = httpRequest;
  }

  @Override
  public void accept(HttpResponse<?> httpResponse, Throwable throwable) {
    instrumenter.end(context, httpRequest, httpResponse, throwable);
  }
}
