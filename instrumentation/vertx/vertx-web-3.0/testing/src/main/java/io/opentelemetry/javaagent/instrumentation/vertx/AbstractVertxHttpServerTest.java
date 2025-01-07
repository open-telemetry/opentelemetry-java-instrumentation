/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx;

import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class AbstractVertxHttpServerTest extends AbstractHttpServerTest<Vertx> {

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setTestPathParam(true);
    // server spans are ended inside of the controller spans
    options.setVerifyServerSpanEndTime(false);
    options.setContextPath("/vertx-app");
    options.setExpectedHttpRoute(
        (endpoint, method) -> {
          if (Objects.equals(method, HttpConstants._OTHER)) {
            return getContextPath() + endpoint.getPath();
          }
          if (Objects.equals(endpoint, ServerEndpoint.NOT_FOUND)) {
            return getContextPath();
          }
          return super.expectedHttpRoute(endpoint, method);
        });
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

    server.deployVerticle(
        verticle().getName(),
        new DeploymentOptions()
            .setConfig(
                new JsonObject().put(AbstractVertxWebServer.CONFIG_HTTP_SERVER_PORT, (Object) port))
            .setInstances(3),
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

  protected abstract Class<? extends AbstractVerticle> verticle();
}
