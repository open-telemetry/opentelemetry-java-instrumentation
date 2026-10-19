/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.ratpack.v1_4.server;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.HttpAttributes.HTTP_ROUTE;
import static io.opentelemetry.semconv.UrlAttributes.URL_PATH;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.common.AggregatedHttpResponse;
import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import ratpack.error.ServerErrorHandler;
import ratpack.exec.Blocking;
import ratpack.exec.Execution;
import ratpack.exec.Promise;
import ratpack.handling.Context;
import ratpack.http.client.HttpClient;
import ratpack.server.RatpackServer;

class RatpackErrorContextTest {

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final boolean CONTROLLER_TELEMETRY_ENABLED =
      Boolean.getBoolean("otel.instrumentation.common.controller-telemetry.enabled");

  private RatpackServer server;
  private final ExecutorService executor = Executors.newFixedThreadPool(2);

  @AfterEach
  void stopServer() throws Exception {
    executor.shutdownNow();
    if (server != null) {
      server.stop();
    }
  }

  @ParameterizedTest
  @EnumSource(ErrorPath.class)
  void errorsUseRequestContext(ErrorPath errorPath) throws Exception {
    CountDownLatch pendingRequests = new CountDownLatch(2);
    ConcurrentHashMap<String, CountDownLatch> pending = new ConcurrentHashMap<>();
    server =
        RatpackServer.start(
            spec ->
                spec.serverConfig(
                        config ->
                            config
                                .port(PortUtils.findOpenPort())
                                .address(InetAddress.getByName("localhost"))
                                .threads(1))
                    .handlers(
                        chain -> {
                          chain.register(
                              registry ->
                                  registry.add(ServerErrorHandler.class, new TestErrorHandler()));
                          chain.get(
                              "error/:id",
                              ctx -> {
                                String id = ctx.getPathTokens().get("id");
                                switch (errorPath) {
                                  case SYNC:
                                    fail(id);
                                    break;
                                  case BLOCKING:
                                    Blocking.get(() -> fail(id)).then(ctx::render);
                                    break;
                                  case ASYNC:
                                    suspend(id, pending, pendingRequests)
                                        .then(RatpackErrorContextTest::fail);
                                    break;
                                  case FORK:
                                    fork(ctx, id, pending, pendingRequests);
                                    break;
                                }
                              });
                        }));

    WebClient client = WebClient.of("h1c://localhost:" + server.getBindPort());
    CompletableFuture<AggregatedHttpResponse> first = client.get("/error/first").aggregate();
    CompletableFuture<AggregatedHttpResponse> second = client.get("/error/second").aggregate();
    if (errorPath == ErrorPath.ASYNC || errorPath == ErrorPath.FORK) {
      assertThat(pendingRequests.await(10, SECONDS)).isTrue();
      // Resume overlapping requests sharing the same event loop.
      pending.get("second").countDown();
      pending.get("first").countDown();
    }
    assertThat(first.get(10, SECONDS).status().code()).isEqualTo(500);
    assertThat(first.join().contentUtf8()).isEqualTo("first");
    assertThat(second.get(10, SECONDS).status().code()).isEqualTo(500);
    assertThat(second.join().contentUtf8()).isEqualTo("second");

    testing.waitAndAssertSortedTraces(
        Comparator.comparing(trace -> trace.get(0).getAttributes().get(URL_PATH)),
        trace -> assertRequestTrace(trace, "first"),
        trace -> assertRequestTrace(trace, "second"));
  }

  @Test
  @EnabledIfSystemProperty(
      named = "otel.instrumentation.common.controller-telemetry.enabled",
      matches = "false")
  void outboundRequestUsesApplicationSpan() throws Exception {
    server =
        RatpackServer.start(
            spec ->
                spec.serverConfig(
                        config ->
                            config
                                .port(PortUtils.findOpenPort())
                                .address(InetAddress.getByName("localhost")))
                    .handlers(
                        chain -> {
                          chain.get(
                              "outbound",
                              ctx ->
                                  testing.runWithSpan(
                                      "application",
                                      () ->
                                          ctx.get(HttpClient.class)
                                              .get(
                                                  new URI(
                                                      "http://localhost:"
                                                          + ctx.getServerConfig().getPort()
                                                          + "/downstream"))
                                              .then(
                                                  response ->
                                                      ctx.render(response.getBody().getText()))));
                          chain.get("downstream", ctx -> ctx.render("success"));
                        }));

    AggregatedHttpResponse response =
        WebClient.of("h1c://localhost:" + server.getBindPort())
            .get("/outbound")
            .aggregate()
            .get(10, SECONDS);
    assertThat(response.status().code()).isEqualTo(200);
    assertThat(response.contentUtf8()).isEqualTo("success");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET /outbound").hasKind(SpanKind.SERVER).hasNoParent(),
                span -> span.hasName("application").hasParent(trace.getSpan(0)),
                span -> span.hasKind(SpanKind.CLIENT).hasParent(trace.getSpan(1)),
                span ->
                    span.hasName("GET /downstream")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(2))));
  }

  private Promise<String> suspend(
      String id,
      ConcurrentHashMap<String, CountDownLatch> pending,
      CountDownLatch pendingRequests) {
    return Promise.async(
        downstream -> {
          CountDownLatch resume = new CountDownLatch(1);
          pending.put(id, resume);
          executor.execute(
              () -> {
                pendingRequests.countDown();
                try {
                  assertThat(resume.await(10, SECONDS)).isTrue();
                  downstream.success(id);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  downstream.error(e);
                }
              });
        });
  }

  private void fork(
      Context ctx,
      String id,
      ConcurrentHashMap<String, CountDownLatch> pending,
      CountDownLatch pendingRequests) {
    Promise.<Void>async(
            done ->
                Execution.fork()
                    .onError(
                        throwable -> {
                          ctx.error(throwable);
                          done.success(null);
                        })
                    .start(
                        execution -> {
                          // Context retains the request registry, not this fork's.
                          assertThat(execution.maybeGet(io.opentelemetry.context.Context.class))
                              .isEmpty();
                          suspend(id, pending, pendingRequests).then(RatpackErrorContextTest::fail);
                        }))
        .then(ignored -> {});
  }

  private static String fail(String id) {
    testing.runWithSpan("callback-" + id, () -> {});
    throw new IllegalStateException(id);
  }

  private static void assertRequestTrace(TraceAssert trace, String id) {
    List<Consumer<SpanDataAssert>> assertions = new ArrayList<>();
    assertions.add(
        span -> {
          span.hasName("GET /error/:id")
              .hasKind(SpanKind.SERVER)
              .hasNoParent()
              .hasStatus(StatusData.error())
              .hasAttributesSatisfying(equalTo(HTTP_ROUTE, "/error/:id"));
          if (CONTROLLER_TELEMETRY_ENABLED) {
            span.hasEventsSatisfyingExactly();
          } else {
            span.hasException(new IllegalStateException(id))
                .hasEventsSatisfyingExactly(event -> event.hasName("exception"));
          }
        });
    if (CONTROLLER_TELEMETRY_ENABLED) {
      assertions.add(
          span ->
              span.hasName("/error/:id")
                  .hasKind(SpanKind.INTERNAL)
                  .hasParent(trace.getSpan(0))
                  .hasStatus(StatusData.error())
                  .hasException(new IllegalStateException(id))
                  .hasEventsSatisfyingExactly(event -> event.hasName("exception")));
    }
    assertions.add(
        span ->
            span.hasName("callback-" + id)
                .hasParent(trace.getSpan(CONTROLLER_TELEMETRY_ENABLED ? 1 : 0))
                .hasStatus(StatusData.unset())
                .hasEventsSatisfyingExactly());
    trace.hasSpansSatisfyingExactly(assertions);
  }

  private enum ErrorPath {
    SYNC,
    BLOCKING,
    ASYNC,
    FORK
  }

  private static class TestErrorHandler implements ServerErrorHandler {
    @Override
    public void error(Context ctx, Throwable throwable) {
      ctx.getResponse().status(500).send(throwable.getMessage());
    }
  }
}
