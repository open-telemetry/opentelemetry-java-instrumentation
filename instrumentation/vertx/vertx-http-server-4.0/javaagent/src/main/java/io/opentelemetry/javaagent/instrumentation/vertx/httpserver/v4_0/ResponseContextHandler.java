/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.vertx.core.Handler;
import javax.annotation.Nullable;

final class ResponseContextHandler<T> implements Handler<T> {
  private final Handler<T> delegate;
  private final Context context;

  private ResponseContextHandler(Handler<T> delegate, Context context) {
    this.delegate = delegate;
    this.context = context;
  }

  @Nullable
  static <T> Handler<T> wrap(@Nullable Handler<T> handler, Context context) {
    if (handler == null) {
      return null;
    }
    if (handler instanceof ResponseContextHandler
        && ((ResponseContextHandler<?>) handler).context == context) {
      return handler;
    }
    return new ResponseContextHandler<>(handler, context);
  }

  @Override
  public void handle(T event) {
    try (Scope ignored = context.makeCurrent()) {
      delegate.handle(event);
    }
  }
}
