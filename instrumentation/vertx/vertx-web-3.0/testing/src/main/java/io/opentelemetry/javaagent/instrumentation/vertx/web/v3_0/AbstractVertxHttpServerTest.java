/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.web.v3_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.javaagent.instrumentation.vertx.web.v3_0.AbstractVertxWebServer.RECOVERED_EXCEPTION;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.api.internal.HttpConstants;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.http.HttpServerTestOptions;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

abstract class AbstractVertxHttpServerTest extends AbstractHttpServerTest<Vertx> {

  private static final ServerEndpoint SUB_ROUTER_PATH_PARAM =
      new ServerEndpoint("SUB_ROUTER_PATH_PARAM", "child/path/123/param", 200, "123", false);

  @RegisterExtension
  static final InstrumentationExtension testing = HttpServerInstrumentationExtension.forAgent();

  @Override
  protected void configure(HttpServerTestOptions options) {
    super.configure(options);
    options.setInstrumentationName("io.opentelemetry.vertx-web-3.0");
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
  void recoveredException() {
    AggregatedHttpResponse response =
        client.execute(request(RECOVERED_EXCEPTION, "GET")).aggregate().join();

    assertThat(response.status().code()).isEqualTo(200);
    IllegalStateException error = new IllegalStateException(RECOVERED_EXCEPTION.getBody());
    AtomicReference<SpanData> serverSpan = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("GET " + getContextPath() + RECOVERED_EXCEPTION.getPath())
                      .hasKind(SpanKind.SERVER)
                      .hasNoParent()
                      .hasStatus(StatusData.unset())
                      .hasException(emitExceptionAsSpanEvents() ? error : null));
          serverSpan.set(trace.getSpan(0));
        });
    if (emitExceptionAsLogs()) {
      assertExceptionLogs(error, "http.server.request.exception", requireNonNull(serverSpan.get()));
    }
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
  @ValueSource(strings = {"same", "wrapped", "distinct"})
  void nestedHandlerExceptions(String error) {
    boolean distinct = "distinct".equals(error);
    ServerEndpoint endpoint =
        new ServerEndpoint("NESTED_EXCEPTIONS", "nested-exceptions?error=" + error, 500, "", false);
    AggregatedHttpResponse response = client.execute(request(endpoint, "GET")).aggregate().join();

    assertThat(response.status().code()).isEqualTo(500);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(SpanKind.SERVER)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .satisfies(
                            data ->
                                assertThat(data.getEvents())
                                    .filteredOn(event -> event.getName().equals("exception"))
                                    .hasSize(emitExceptionAsSpanEvents() ? 2 : 0))));

    if (!emitExceptionAsLogs()) {
      assertThat(testing.logRecords()).isEmpty();
      return;
    }
    List<LogRecordData> logs = testing.waitForLogRecords(2);
    assertThat(logs).hasSize(2);
    assertThat(logs)
        .extracting(log -> log.getAttributes().get(EXCEPTION_MESSAGE))
        .containsExactly(
            distinct
                ? new String[] {"shared failure", "different failure"}
                : new String[] {"shared failure", "shared failure"});
    assertThat(logs)
        .extracting(log -> log.getAttributes().get(EXCEPTION_TYPE))
        .containsExactly(
            distinct
                ? new String[] {
                  IllegalStateException.class.getName(), IllegalArgumentException.class.getName()
                }
                : new String[] {
                  IllegalStateException.class.getName(), IllegalStateException.class.getName()
                });
    assertThat(logs)
        .allSatisfy(
            log -> {
              assertThat(log.getEventName()).isEqualTo("http.server.request.exception");
              assertThat(log.getInstrumentationScopeInfo().getName())
                  .isEqualTo("io.opentelemetry.vertx-web-3.0");
              assertThat(log.getSpanContext()).isEqualTo(testing.spans().get(0).getSpanContext());
            });
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
