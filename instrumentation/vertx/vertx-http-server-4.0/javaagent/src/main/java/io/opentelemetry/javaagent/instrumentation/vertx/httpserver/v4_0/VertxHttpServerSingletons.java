/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.internal.JavaagentHttpServerInstrumenters;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import java.util.logging.Logger;
import javax.annotation.Nullable;

public final class VertxHttpServerSingletons {
  public static final VirtualField<HttpServerRequest, VertxHttpServerState> STATE =
      VirtualField.find(HttpServerRequest.class, VertxHttpServerState.class);
  public static final VirtualField<HttpServerResponse, VertxHttpServerResponseState>
      RESPONSE_STATE =
          VirtualField.find(HttpServerResponse.class, VertxHttpServerResponseState.class);

  private static final Instrumenter<HttpServerRequest, HttpServerResponse> instrumenter =
      JavaagentHttpServerInstrumenters.create(
          "io.opentelemetry.vertx-http-server-4.0",
          new VertxHttpServerAttributesGetter(),
          new HttpServerRequestHeaderGetter());
  private static final Logger logger = Logger.getLogger(VertxHttpServerSingletons.class.getName());

  @Nullable
  static VertxHttpServerState start(HttpServerRequest request) {
    try {
      VertxHttpServerState state = STATE.get(request);
      if (state == null) {
        Context parent = Context.current();
        boolean started = instrumenter.shouldStart(parent, request);
        state =
            new VertxHttpServerState(
                started ? instrumenter.start(parent, request) : parent, started);
        STATE.set(request, state);
        RESPONSE_STATE.set(request.response(), new VertxHttpServerResponseState(request, state));
      }
      return state;
    } catch (Throwable t) {
      logger.log(FINE, "Unable to start Vert.x HTTP server span", t);
      return null;
    }
  }

  public static void end(HttpServerRequest request, @Nullable Throwable error) {
    try {
      VertxHttpServerState state = STATE.get(request);
      if (state != null && state.ended.compareAndSet(false, true)) {
        STATE.set(request, null);
        RESPONSE_STATE.set(request.response(), null);
        if (state.started) {
          instrumenter.end(state.context, request, request.response(), error);
        }
      }
    } catch (Throwable t) {
      logger.log(FINE, "Unable to end Vert.x HTTP server span", t);
    }
  }

  public static void endResponse(HttpServerResponse response, @Nullable Throwable error) {
    try {
      VertxHttpServerResponseState state = RESPONSE_STATE.get(response);
      if (state != null) {
        HttpServerRequest request = state.request.get();
        if (request != null) {
          end(request, error);
        } else {
          RESPONSE_STATE.set(response, null);
        }
      }
    } catch (Throwable t) {
      logger.log(FINE, "Unable to end Vert.x HTTP server response span", t);
    }
  }

  private VertxHttpServerSingletons() {}
}
