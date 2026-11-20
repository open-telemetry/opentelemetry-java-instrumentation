/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.web.v3_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ClientAttributes.CLIENT_ADDRESS;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_REQUEST_METHOD;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PROTOCOL_VERSION;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static io.opentelemetry.semconv.UrlAttributes.URL_SCHEME;
import static io.opentelemetry.semconv.UserAgentAttributes.USER_AGENT_ORIGINAL;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.opentelemetry.api.trace.SpanKind;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

  @ParameterizedTest
  @CsvSource({
    "/standard, /standard, /standard",
    "/virtual, /virtual, /virtual",
    "/repeat/repeat, /repeat/repeat, GET /repeat/repeat",
    "/precedence/123, /precedence/:id, GET /precedence/:id",
    "/vertx-app/standard, /vertx-app/standard, /vertx-app/standard",
    "/vertx-app/child/standard, /vertx-app/child/standard, /vertx-app/child/standard",
    "/vertx-app/child/repeat/repeat, /vertx-app/child/repeat/repeat, GET /vertx-app/child/repeat/repeat"
  })
  void multiHandlerRoute(String path, String expectedRoute, String expectedName) {
    assumeTrue(
        supportsMultiHandlerRoutes(), "Vert.x 3.0 replaces rather than appends route handlers");
    AggregatedHttpResponse response =
        client
            .get(address.resolve(path).toString().replace("http://", "h1c://"))
            .aggregate()
            .join();

    assertThat(response.status().code()).isEqualTo(200);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(expectedName)
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(HTTP_ROUTE, expectedRoute),
                            equalTo(HTTP_REQUEST_METHOD, "GET"),
                            equalTo(HTTP_RESPONSE_STATUS_CODE, 200),
                            equalTo(SERVER_ADDRESS, "localhost"),
                            equalTo(SERVER_PORT, port),
                            equalTo(CLIENT_ADDRESS, TEST_CLIENT_IP),
                            equalTo(NETWORK_PEER_ADDRESS, "127.0.0.1"),
                            satisfies(NETWORK_PEER_PORT, val -> val.isPositive()),
                            equalTo(NETWORK_PROTOCOL_VERSION, "1.1"),
                            equalTo(USER_AGENT_ORIGINAL, TEST_USER_AGENT),
                            equalTo(URL_SCHEME, "http"),
                            equalTo(URL_PATH, path))));
  }

  protected boolean supportsMultiHandlerRoutes() {
    return true;
  }

  protected void configureDeploymentOptions(DeploymentOptions options) {}

  @Override
  protected Vertx setupServer() throws ExecutionException, InterruptedException, TimeoutException {
    Vertx server =
        Vertx.vertx(
            new VertxOptions()
            // Useful for debugging:
            // .setBlockedThreadCheckInterval(Integer.MAX_VALUE)
            );
    CompletableFuture<Void> future = new CompletableFuture<>();

    DeploymentOptions deploymentOptions =
        new DeploymentOptions()
            // casting port to Object because the put override that takes Integer is removed in
            // later versions of vertx-core
            .setConfig(
                new JsonObject().put(AbstractVertxWebServer.CONFIG_HTTP_SERVER_PORT, (Object) port))
            .setInstances(3);
    configureDeploymentOptions(deploymentOptions);

    server.deployVerticle(
        verticle().getName(),
        deploymentOptions,
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
