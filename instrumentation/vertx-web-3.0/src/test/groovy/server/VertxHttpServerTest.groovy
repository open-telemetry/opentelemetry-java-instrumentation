/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM

import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class VertxHttpServerTest extends HttpServerTest<Vertx> implements AgentTestTrait {
  @Override
  Vertx startServer(int port) {
    Vertx server = Vertx.vertx(new VertxOptions()
    // Useful for debugging:
    // .setBlockedThreadCheckInterval(Integer.MAX_VALUE)
      .setClusterPort(port))
    CompletableFuture<Void> future = new CompletableFuture<>()
    server.deployVerticle(verticle().getName(),
      new DeploymentOptions()
        .setConfig(new JsonObject().put(VertxWebServer.CONFIG_HTTP_SERVER_PORT, port))
        .setInstances(3)) { res ->
      if (!res.succeeded()) {
        throw new RuntimeException("Cannot deploy server Verticle", res.cause())
      }
      future.complete(null)
    }

    future.get(30, TimeUnit.SECONDS)
    return server
  }

  protected Class<? extends AbstractVerticle> verticle() {
    return VertxWebServer
  }

  @Override
  void stopServer(Vertx server) {
    server.close()
  }

  @Override
  boolean testException() {
    // TODO(anuraaga): https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/807
    return false
  }

  @Override
  boolean testPathParam() {
    return true
  }

  @Override
  boolean testNotFound() {
    return false
  }

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    return endpoint == PATH_PARAM ? "/path/:id/param" : endpoint.getPath()
  }

}
