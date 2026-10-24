/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import static io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0.VertxHttpServerSingletons.RESPONSE_STATE;
import static io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0.VertxHttpServerSingletons.STATE;

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
  private volatile boolean completeOnlyWithoutTrailers;

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
    this(response, state, delegate, completeResponse, false);
  }

  public ResponseCompletionHandler(
      HttpServerResponse response,
      VertxHttpServerState state,
      @Nullable Handler<AsyncResult<Void>> delegate,
      boolean completeResponse,
      boolean completeOnlyWithoutTrailers) {
    this.response = response;
    this.state = state;
    this.delegate = delegate;
    this.completeResponse = completeResponse;
    this.completeOnlyWithoutTrailers = completeOnlyWithoutTrailers;
  }

  void setCompleteOnlyWithoutTrailers(boolean completeOnlyWithoutTrailers) {
    this.completeOnlyWithoutTrailers = completeOnlyWithoutTrailers;
  }

  @Override
  public void handle(AsyncResult<Void> result) {
    if (completeResponse && !completeOnlyWithoutTrailers) {
      VertxHttpServerSingletons.endResponse(response, result.cause());
    }
    if (delegate != null) {
      try (Scope ignored = state.context.makeCurrent()) {
        delegate.handle(result);
      }
    }
  }
}
