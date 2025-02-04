/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.reactive.server;

import static io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint.EXCEPTION;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.vertx.core.Future;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import org.junit.jupiter.api.extension.RegisterExtension;

class VertxRxHttpServerTest extends AbstractVertxRxHttpServerTest {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected Class<? extends AbstractVerticle> verticle() {
    return VertxReactiveWebServer.class;
  }

  public static class VertxReactiveWebServer extends AbstractVertxRxVerticle {
    @Override
    void handle(RoutingContext ctx, ServerEndpoint endpoint, Runnable action) {
      controller(endpoint, action::run);
    }

    @Override
    public void start(Future<Void> startFuture) {
      int port = config().getInteger(CONFIG_HTTP_SERVER_PORT);
      Router router = Router.router(vertx);

      configure(router);
      router
          .route(EXCEPTION.getPath())
          .handler(
              ctx ->
                  handle(
                      ctx,
                      EXCEPTION,
                      () -> {
                        throw new IllegalStateException(EXCEPTION.getBody());
                      }));

      vertx
          .createHttpServer()
          .requestHandler(router::accept)
          .listen(port, httpServerAsyncResult -> startFuture.complete());
    }
  }
}
