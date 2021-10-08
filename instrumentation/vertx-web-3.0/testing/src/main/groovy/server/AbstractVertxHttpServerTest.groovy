/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package server

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.instrumentation.test.AgentTestTrait
import io.opentelemetry.instrumentation.test.base.HttpServerTest
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import io.vertx.core.AbstractVerticle
import io.vertx.core.DeploymentOptions
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.core.json.JsonObject

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.NOT_FOUND
import static io.opentelemetry.instrumentation.test.base.HttpServerTest.ServerEndpoint.PATH_PARAM

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
  boolean testConcurrency() {
    return true
  }

  @Override
  boolean testCapturedHttpHeaders() {
    false
  }

  @Override
  List<AttributeKey<?>> extraAttributes() {
    return [
      SemanticAttributes.HTTP_URL
    ]
  }

  @Override
  String expectedServerSpanName(ServerEndpoint endpoint) {
    switch (endpoint) {
      case PATH_PARAM:
        return "/path/:id/param"
      case NOT_FOUND:
        return "HTTP GET"
      default:
        return endpoint.getPath()
    }
  }

}
