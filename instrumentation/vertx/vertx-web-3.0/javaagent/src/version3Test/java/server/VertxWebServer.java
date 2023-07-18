/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server;

import static server.VertxRouterBuddy.CONFIG_HTTP_SERVER_PORT;
import static server.VertxRouterBuddy.buildRouter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.ext.web.Router;

public class VertxWebServer extends AbstractVerticle {

  @Override
  public void start(Future<Void> startFuture) {
    int port = config().getInteger(CONFIG_HTTP_SERVER_PORT);
    Router router = buildRouter(vertx);
    vertx
        .createHttpServer()
        .requestHandler(router::accept)
        .listen(port, it -> startFuture.complete());
  }
}
