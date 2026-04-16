/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.client;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

public class ExceptionHandlerWrapper implements Handler<Throwable> {
  private final Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter;
  private final HttpClientRequest request;
  private final VirtualField<HttpClientRequest, Contexts> virtualField;
  private final Handler<Throwable> handler;

  private ExceptionHandlerWrapper(
      Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter,
      HttpClientRequest request,
      VirtualField<HttpClientRequest, Contexts> virtualField,
      Handler<Throwable> handler) {
    this.instrumenter = instrumenter;
    this.request = request;
    this.virtualField = virtualField;
    this.handler = handler;
  }

  public static Handler<Throwable> wrap(
      Instrumenter<HttpClientRequest, HttpClientResponse> instrumenter,
      HttpClientRequest request,
      VirtualField<HttpClientRequest, Contexts> virtualField,
      Handler<Throwable> handler) {
    if (handler instanceof ExceptionHandlerWrapper) {
      return handler;
    }

    return new ExceptionHandlerWrapper(instrumenter, request, virtualField, handler);
  }

  @Override
  public void handle(Throwable throwable) {
    Contexts contexts = virtualField.get(request);
    if (contexts == null) {
      callHandler(throwable);
      return;
    }

    instrumenter.end(contexts.context, request, null, throwable);

    try (Scope ignored = contexts.parentContext.makeCurrent()) {
      callHandler(throwable);
    }
  }

  private void callHandler(Throwable throwable) {
    handler.handle(throwable);
  }
}
