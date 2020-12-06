/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import java.net.http.HttpResponse;
import java.util.function.BiConsumer;

public class ResponseConsumer implements BiConsumer<HttpResponse<?>, Throwable> {
  private final HttpClientOperation<HttpResponse<?>> operation;

  public ResponseConsumer(HttpClientOperation<HttpResponse<?>> operation) {
    this.operation = operation;
  }

  @Override
  public void accept(HttpResponse<?> httpResponse, Throwable throwable) {
    operation.endMaybeExceptionally(httpResponse, throwable);
  }
}
