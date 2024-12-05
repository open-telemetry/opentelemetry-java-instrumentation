/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;

public class VertxWebServer extends AbstractVertxWebServer {

  @Override
  public void end(HttpServerResponse response) {
    response.end();
  }

  @Override
  public void end(HttpServerResponse response, String message) {
    response.end(message);
  }

  @Override
  public void start(Future<Void> startFuture) {
    int port = config().getInteger(CONFIG_HTTP_SERVER_PORT);
    Router router = buildRouter();
    Router mainRouter = Router.router(vertx);
    mainRouter.mountSubRouter("/vertx-app", router);

    vertx
        .createHttpServer()
        .requestHandler(mainRouter::accept)
        .listen(port, it -> startFuture.complete());
  }
}
