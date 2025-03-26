/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;

public class VertxLatestWebServer extends AbstractVertxWebServer {

  @Override
  public void end(HttpServerResponse response) {
    response.end();
  }

  @Override
  public void end(HttpServerResponse response, String message) {
    response.end(message);
  }

  @Override
  public void start(Promise<Void> startPromise) {
    int port = config().getInteger(CONFIG_HTTP_SERVER_PORT);
    Router router = buildRouter();
    Router mainRouter = Router.router(vertx);
    mainRouter.route("/vertx-app/*").subRouter(router);

    vertx.createHttpServer().requestHandler(mainRouter).listen(port, it -> startPromise.complete());
  }
}
