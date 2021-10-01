/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.client;

import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientRequest;

public class ExceptionHandlerWrapper implements Handler<Throwable> {
  private final AbstractVertxClientTracer tracer;
  private final HttpClientRequest request;
  private final VirtualField<HttpClientRequest, Contexts> virtualField;
  private final Handler<Throwable> handler;

  private ExceptionHandlerWrapper(
      AbstractVertxClientTracer tracer,
      HttpClientRequest request,
      VirtualField<HttpClientRequest, Contexts> virtualField,
      Handler<Throwable> handler) {
    this.tracer = tracer;
    this.request = request;
    this.virtualField = virtualField;
    this.handler = handler;
  }

  public static Handler<Throwable> wrap(
      AbstractVertxClientTracer tracer,
      HttpClientRequest request,
      VirtualField<HttpClientRequest, Contexts> virtualField,
      Handler<Throwable> handler) {
    if (handler instanceof ExceptionHandlerWrapper) {
      return handler;
    }

    return new ExceptionHandlerWrapper(tracer, request, virtualField, handler);
  }

  @Override
  public void handle(Throwable throwable) {
    Contexts contexts = virtualField.get(request);
    if (contexts == null) {
      callHandler(throwable);
      return;
    }

    tracer.endExceptionally(contexts.context, throwable);

    try (Scope ignored = contexts.parentContext.makeCurrent()) {
      callHandler(throwable);
    }
  }

  private void callHandler(Throwable throwable) {
    handler.handle(throwable);
  }
}
