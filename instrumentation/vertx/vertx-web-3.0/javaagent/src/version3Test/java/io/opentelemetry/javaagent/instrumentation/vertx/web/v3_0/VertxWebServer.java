/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.web.v3_0;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;

public class VertxWebServer extends AbstractVertxWebServer {

  @Override
  void addMultiHandlerRoutes(Router router) {
    if (testLatestDeps()) {
      super.addMultiHandlerRoutes(router);
    }
  }

  @Override
  void end(HttpServerResponse response) {
    response.end();
  }

  @Override
  void end(HttpServerResponse response, String message) {
    response.end(message);
  }

  @Override
  public void start(Future<Void> startFuture) {
    int port = config().getInteger(CONFIG_HTTP_SERVER_PORT);
    Router router = buildRouter();
    router.mountSubRouter("/child", buildRouter());

    Router mainRouter = Router.router(vertx);
    addMultiHandlerRoutes(mainRouter);
    mainRouter.mountSubRouter("/vertx-app", router);

    vertx
        .createHttpServer()
        .requestHandler(mainRouter::accept)
        .listen(
            port,
            result -> {
              if (result.succeeded()) {
                startFuture.complete();
              } else {
                startFuture.fail(result.cause());
              }
            });
  }
}
