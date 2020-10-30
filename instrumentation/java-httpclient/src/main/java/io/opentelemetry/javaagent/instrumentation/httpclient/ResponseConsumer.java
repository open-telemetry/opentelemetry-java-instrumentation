/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import static io.opentelemetry.javaagent.instrumentation.httpclient.JdkHttpClientTracer.TRACER;

import io.opentelemetry.api.trace.Span;
import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public class ResponseConsumer implements BiConsumer<HttpResponse<?>, Throwable> {
  private final Span span;

  public ResponseConsumer(Span span) {
    this.span = span;
  }

  @Override
  public void accept(HttpResponse<?> httpResponse, Throwable throwable) {
    if (throwable == null) {
      TRACER.end(span, httpResponse);
    } else {
      TRACER.endExceptionally(span, httpResponse, throwable);
    }
  }
}
