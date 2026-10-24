/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import io.opentelemetry.context.Scope;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;

public final class ServerRequestHandler implements Handler<HttpServerRequest> {
  private final Handler<HttpServerRequest> delegate;

  public ServerRequestHandler(Handler<HttpServerRequest> delegate) {
    this.delegate = delegate;
  }

  @Override
  public void handle(HttpServerRequest request) {
    VertxHttpServerState state = VertxHttpServerSingletons.start(request);
    if (state == null) {
      delegate.handle(request);
      return;
    }
    try (Scope ignored = state.context.makeCurrent()) {
      delegate.handle(request);
    } catch (Throwable t) {
      VertxHttpServerSingletons.end(request, t);
      throw t;
    }
  }
}
