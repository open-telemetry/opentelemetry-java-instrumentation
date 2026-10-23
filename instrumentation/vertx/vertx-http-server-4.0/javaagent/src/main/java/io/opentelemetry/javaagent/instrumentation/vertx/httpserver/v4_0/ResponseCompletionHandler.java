/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import io.opentelemetry.context.Scope;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import javax.annotation.Nullable;

public final class ResponseCompletionHandler implements Handler<AsyncResult<Void>> {
  private final HttpServerResponse response;
  private final VertxHttpServerState state;
  @Nullable private final Handler<AsyncResult<Void>> delegate;

  public ResponseCompletionHandler(
      HttpServerResponse response,
      VertxHttpServerState state,
      @Nullable Handler<AsyncResult<Void>> delegate) {
    this.response = response;
    this.state = state;
    this.delegate = delegate;
  }

  @Override
  public void handle(AsyncResult<Void> result) {
    VertxHttpServerSingletons.endResponse(response, result.cause());
    if (delegate != null) {
      try (Scope ignored = state.context.makeCurrent()) {
        delegate.handle(result);
      }
    }
  }
}
