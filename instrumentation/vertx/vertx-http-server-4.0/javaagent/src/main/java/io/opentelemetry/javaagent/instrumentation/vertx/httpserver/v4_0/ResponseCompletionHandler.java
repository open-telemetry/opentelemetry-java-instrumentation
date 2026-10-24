/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0.VertxHttpServerSingletons.RESPONSE_STATE;
import static io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0.VertxHttpServerSingletons.STATE;
import static io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0.VertxHttpServerSingletons.currentWriteState;

import io.opentelemetry.context.Scope;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import javax.annotation.Nullable;

public final class ResponseCompletionHandler implements Handler<AsyncResult<Void>> {
  private final HttpServerResponse response;
  private final VertxHttpServerState state;
  @Nullable private final Handler<AsyncResult<Void>> delegate;
  private final boolean completeResponse;

  static CurrentWriteState writeState(HttpServerResponse response) {
    VertxHttpServerResponseState responseState = RESPONSE_STATE.get(response);
    HttpServerRequest request = responseState == null ? null : responseState.request.get();
    Object connection = request == null ? null : request.connection();
    VertxHttpServerState state = responseState == null ? null : responseState.state;
    return new CurrentWriteState(response, state, connection);
  }

  static final class WriteState {
    @Nullable CurrentWriteState previous;
  }

  @Nullable
  public static Handler<AsyncResult<Void>> wrapHeaders(
      Object streamRequest, boolean end, @Nullable Handler<AsyncResult<Void>> handler) {
    if (end && streamRequest instanceof HttpServerRequest) {
      HttpServerRequest request = (HttpServerRequest) streamRequest;
      VertxHttpServerState state = STATE.get(request);
      if (state == null) {
        VertxHttpServerResponseState responseState = RESPONSE_STATE.get(request.response());
        state = responseState == null ? null : responseState.state;
      }
      if (state != null) {
        return new ResponseCompletionHandler(request.response(), state, handler);
      }
    }
    return handler;
  }

  @Nullable
  public static Handler<AsyncResult<Void>> wrapHeaders(
      HttpServerResponse response, boolean end, @Nullable Handler<AsyncResult<Void>> handler) {
    if (!end) {
      return handler;
    }
    VertxHttpServerResponseState state = RESPONSE_STATE.get(response);
    return state == null ? handler : new ResponseCompletionHandler(response, state.state, handler);
  }

  @Nullable
  public static Handler<AsyncResult<Void>> wrapData(
      Object connection, boolean end, @Nullable Handler<AsyncResult<Void>> handler) {
    CurrentWriteState state = currentWriteState().get();
    if (state == null || state.state == null || state.connection != connection) {
      return handler;
    }
    return new ResponseCompletionHandler(state.response, state.state, handler, end);
  }

  public ResponseCompletionHandler(
      HttpServerResponse response,
      VertxHttpServerState state,
      @Nullable Handler<AsyncResult<Void>> delegate) {
    this(response, state, delegate, true);
  }

  public ResponseCompletionHandler(
      HttpServerResponse response,
      VertxHttpServerState state,
      @Nullable Handler<AsyncResult<Void>> delegate,
      boolean completeResponse) {
    this.response = response;
    this.state = state;
    this.delegate = delegate;
    this.completeResponse = completeResponse;
  }

  @Override
  public void handle(AsyncResult<Void> result) {
    if (completeResponse) {
      VertxHttpServerSingletons.endResponse(response, result.cause());
    }
    if (delegate != null) {
      try (Scope ignored = state.context.makeCurrent()) {
        delegate.handle(result);
      }
    }
  }

  public static final class CurrentWriteState {
    private final HttpServerResponse response;
    @Nullable private final VertxHttpServerState state;
    @Nullable private final Object connection;

    private CurrentWriteState(
        HttpServerResponse response,
        @Nullable VertxHttpServerState state,
        @Nullable Object connection) {
      this.response = response;
      this.state = state;
      this.connection = connection;
    }
  }
}
