/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server


import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

abstract class AbstractVertxHttpServerTest extends HttpServerTest<Vertx> implements AgentTestTrait {
  @Override
  Vertx startServer(int port) {
    Vertx server = Vertx.vertx(new VertxOptions()
      // Useful for debugging:
      // .setBlockedThreadCheckInterval(Integer.MAX_VALUE)
    )
    CompletableFuture<Void> future = new CompletableFuture<>()
    server.deployVerticle(verticle().getName(),
      new DeploymentOptions()
        .setConfig(new JsonObject().put(AbstractVertxWebServer.CONFIG_HTTP_SERVER_PORT, port))
        .setInstances(3)) { res ->
      if (!res.succeeded()) {
        throw new IllegalStateException("Cannot deploy server Verticle", res.cause())
      }
      future.complete(null)
    }

    future.get(30, TimeUnit.SECONDS)
    return server
  }

  protected abstract Class<? extends AbstractVerticle> verticle()

  @Override
  void stopServer(Vertx server) {
    server.close()
  }

  @Override
  boolean testPathParam() {
    return true
  }

  @Override
  boolean verifyServerSpanEndTime() {
    // server spans are ended inside of the controller spans
    return false
  }
}
