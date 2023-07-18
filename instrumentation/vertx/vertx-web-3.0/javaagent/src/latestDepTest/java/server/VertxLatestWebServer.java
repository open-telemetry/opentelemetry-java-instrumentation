/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server;

import static server.VertxRouterBuddy.CONFIG_HTTP_SERVER_PORT;
import static server.VertxRouterBuddy.buildRouter;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.web.Router;

public class VertxLatestWebServer extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) {
    int port = config().getInteger(CONFIG_HTTP_SERVER_PORT);
    Router router = buildRouter(vertx);
    vertx.createHttpServer().requestHandler(router).listen(port, it -> startPromise.complete());
  }
}
