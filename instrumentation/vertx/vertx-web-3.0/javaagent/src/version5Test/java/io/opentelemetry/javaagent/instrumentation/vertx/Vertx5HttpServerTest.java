/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

class Vertx5HttpServerTest extends AbstractVertxHttpServerTest {

  @Override
  protected Class<? extends AbstractVerticle> verticle() {
    return Vertx5WebServer.class;
  }

  @Override
  protected Vertx setupServer()
      throws ExecutionException, InterruptedException, TimeoutException, NoSuchMethodException {
    Vertx server =
        Vertx.vertx(
            new VertxOptions()
            // Useful for debugging:
            // .setBlockedThreadCheckInterval(Integer.MAX_VALUE)
            );
    CompletableFuture<Void> future = new CompletableFuture<>();

    server
        .deployVerticle(
            verticle().getName(),
            new DeploymentOptions()
                .setConfig(
                    new JsonObject()
                        .put(AbstractVertxWebServer.CONFIG_HTTP_SERVER_PORT, (Object) port))
                .setInstances(3))
        .onComplete(
            res -> {
              if (!res.succeeded()) {
                throw new IllegalStateException("Cannot deploy server Verticle", res.cause());
              }
              future.complete(null);
            });

    future.get(30, TimeUnit.SECONDS);
    return server;
  }

  @Override
  protected void stopServer(Vertx server) throws Exception {
    server.close();
  }
}
