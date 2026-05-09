/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.web.v3_0;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

abstract class AbstractVertxHttpServerTest extends AbstractHttpServerTest<Vertx> {

  private static final ServerEndpoint SUB_ROUTER_PATH_PARAM =
      new ServerEndpoint("SUB_ROUTER_PATH_PARAM", "child/path/123/param", 200, "123", false);

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setTestPathParam(true);
    options.setTestHttpBodyPipelining(true);
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
          if (Objects.equals(endpoint, SUB_ROUTER_PATH_PARAM)) {
            return getContextPath() + "/child/path/:id/param";
          }
          return super.expectedHttpRoute(endpoint, method);
        });
  }

  @Test
  void subRouterRouteIncludesMountPath() {
    String method = "GET";
    AggregatedHttpResponse response =
        client.execute(request(SUB_ROUTER_PATH_PARAM, method)).aggregate().join();

    assertThat(response.status().code()).isEqualTo(SUB_ROUTER_PATH_PARAM.getStatus());
    assertThat(response.contentUtf8()).isEqualTo(SUB_ROUTER_PATH_PARAM.getBody());

    assertTheTraces(1, null, null, null, method, SUB_ROUTER_PATH_PARAM);
  }

  @Override
  protected Vertx setupServer() throws ExecutionException, InterruptedException, TimeoutException {
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
            // casting port to Object because the put override that takes Integer is removed in
            // later versions of vertx-core
            .setConfig(
                new JsonObject().put(AbstractVertxWebServer.CONFIG_HTTP_SERVER_PORT, (Object) port))
            .setInstances(3),
        res -> {
          if (!res.succeeded()) {
            future.completeExceptionally(
                new IllegalStateException("Cannot deploy server Verticle", res.cause()));
            return;
          }
          future.complete(null);
        });

    future.get(30, SECONDS);
    return server;
  }

  @Override
  protected void stopServer(Vertx server) throws Exception {
    server.close();
  }

  protected abstract Class<? extends AbstractVerticle> verticle();
}
