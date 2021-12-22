/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server;

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

    vertx.createHttpServer().requestHandler(router).listen(port, it -> startPromise.complete());
  }
}
