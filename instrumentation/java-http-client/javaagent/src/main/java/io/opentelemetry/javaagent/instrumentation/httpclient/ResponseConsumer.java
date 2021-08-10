/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import static io.opentelemetry.javaagent.instrumentation.httpclient.JdkHttpClientSingletons.instrumenter;

import io.opentelemetry.context.Context;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public class ResponseConsumer implements BiConsumer<HttpResponse<?>, Throwable> {
  private final Context context;
  private final HttpRequest httpRequest;

  public ResponseConsumer(Context context, HttpRequest httpRequest) {
    this.context = context;
    this.httpRequest = httpRequest;
  }

  @Override
  public void accept(HttpResponse<?> httpResponse, Throwable throwable) {
    instrumenter().end(context, httpRequest, httpResponse, throwable);
  }
}
