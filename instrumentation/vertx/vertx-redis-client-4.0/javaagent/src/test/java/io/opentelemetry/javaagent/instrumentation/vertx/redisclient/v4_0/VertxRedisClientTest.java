/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.redisclient.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_REDIS_DATABASE_INDEX;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static java.util.Collections.nCopies;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.Request;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;

@SuppressWarnings("deprecation") // using deprecated semconv
class VertxRedisClientTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final String LONG_BATCH_KEY = String.join("", nCopies(1020, "x"));

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);
  private static String host;
  private static String ip;
  private static int port;
  private static Vertx vertx;
  private static Redis client;
  private static RedisConnection connection;
  private static RedisAPI redis;

  @BeforeAll
  static void setup() throws Exception {
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);

    vertx = Vertx.vertx();
    cleanup.deferAfterAll(vertx::close);
    client = Redis.createClient(vertx, "redis://" + host + ":" + port + "/1");
    cleanup.deferAfterAll(client::close);
    connection = client.connect().toCompletionStage().toCompletableFuture().get(30, SECONDS);
    redis = RedisAPI.api(connection);
    cleanup.deferAfterAll(redis::close);
  }

  @Test
  void setCommand() throws Exception {
    redis.set(asList("foo", "bar")).toCompletionStage().toCompletableFuture().get(30, SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("SET", "SET foo ?"))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.vertx-redis-client-4.0",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_NAMESPACE,
        SERVER_ADDRESS,
        SERVER_PORT,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT);
  }

  @Test
  void getCommand() throws Exception {
    redis.set(asList("foo", "bar")).toCompletionStage().toCompletableFuture().get(30, SECONDS);
    String value =
        redis.get("foo").toCompletionStage().toCompletableFuture().get(30, SECONDS).toString();

    assertThat(value).isEqualTo("bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("SET", "SET foo ?"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("GET", "GET foo"))));
  }

  @Test
  void getCommandWithParent() throws Exception {
    redis.set(asList("foo", "bar")).toCompletionStage().toCompletableFuture().get(30, SECONDS);

    CompletableFuture<String> future = new CompletableFuture<>();
    CompletableFuture<String> result =
        future.whenComplete((value, throwable) -> testing.runWithSpan("callback", () -> {}));

    testing.runWithSpan(
        "parent",
        () ->
            redis
                .get("foo")
                .toCompletionStage()
                .toCompletableFuture()
                .whenComplete(
                    (response, throwable) -> {
                      if (throwable == null) {
                        future.complete(response.toString());
                      } else {
                        future.completeExceptionally(throwable);
                      }
                    }));

    String value = result.get(30, SECONDS);
    assertThat(value).isEqualTo("bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("SET", "SET foo ?"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("GET", "GET foo")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void commandWithNoArguments() throws Exception {
    redis.set(asList("foo", "bar")).toCompletionStage().toCompletableFuture().get(30, SECONDS);

    String value =
        redis.randomkey().toCompletionStage().toCompletableFuture().get(30, SECONDS).toString();

    assertThat(value).isEqualTo("foo");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(redisSpanAttributes("SET", "SET foo ?"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "RANDOMKEY " + host + ":" + port
                                : "RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            redisSpanAttributes("RANDOMKEY", "RANDOMKEY"))));
  }

  @ParameterizedTest
  @MethodSource("batchScenarios")
  void batchCommand(BatchScenario scenario) throws Exception {
    testing.clearData();

    connection.batch(scenario.requests).toCompletionStage().toCompletableFuture().get(30, SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? scenario.operationName + " " + host + ":" + port
                                : scenario.operationName)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            redisSpanAttributes(
                                scenario.operationName, scenario.queryText, scenario.batchSize))));
  }

  private static Stream<Arguments> batchScenarios() {
    // No empty scenario: Vert.x Redis never completes client.batch(emptyList()),
    // and times out before asserting instrumentation.
    return Stream.of(
        Arguments.argumentSet(
            "single",
            BatchScenario.builder()
                .addRequest(Request.cmd(Command.SET).arg("batch1").arg("v1"))
                .operationName("SET")
                .queryText("SET batch1 ?")
                .build()),
        Arguments.argumentSet(
            "twoSameOperation",
            BatchScenario.builder()
                .addRequest(Request.cmd(Command.SET).arg("batch1").arg("v1"))
                .addRequest(Request.cmd(Command.SET).arg("batch2").arg("v2"))
                .operationName("PIPELINE SET")
                .queryText("SET batch1 ?;SET batch2 ?")
                .batchSize(2)
                .build()),
        Arguments.argumentSet(
            "twoDifferentOperations",
            BatchScenario.builder()
                .addRequest(Request.cmd(Command.SET).arg("batch1").arg("v1"))
                .addRequest(Request.cmd(Command.GET).arg("batch1"))
                .operationName("PIPELINE")
                .queryText("SET batch1 ?;GET batch1")
                .batchSize(2)
                .build()),
        Arguments.argumentSet(
            "large",
            BatchScenario.builder()
                .requests(
                    Stream.generate(() -> Request.cmd(Command.GET).arg(LONG_BATCH_KEY))
                        .limit(33)
                        .collect(toList()))
                .operationName("PIPELINE GET")
                .queryText(String.join(";", nCopies(31, "GET " + LONG_BATCH_KEY)))
                .batchSize(33)
                .build()));
  }

  private static AttributeAssertion[] redisSpanAttributes(String operationName, String queryText) {
    return redisSpanAttributes(operationName, queryText, null);
  }

  private static AttributeAssertion[] redisSpanAttributes(
      String operationName, String queryText, Long batchSize) {
    // not testing database/dup
    if (emitStableDatabaseSemconv()) {
      return new AttributeAssertion[] {
        equalTo(DB_SYSTEM_NAME, REDIS),
        equalTo(DB_QUERY_TEXT, queryText),
        equalTo(DB_OPERATION_NAME, operationName),
        equalTo(DB_NAMESPACE, "1"),
        equalTo(DB_OPERATION_BATCH_SIZE, batchSize),
        equalTo(SERVER_ADDRESS, host),
        equalTo(SERVER_PORT, port),
        equalTo(maybeStablePeerService(), "test-peer-service"),
        equalTo(NETWORK_PEER_PORT, port),
        equalTo(NETWORK_PEER_ADDRESS, ip)
      };
    } else {
      return new AttributeAssertion[] {
        equalTo(DB_SYSTEM, REDIS),
        equalTo(DB_STATEMENT, queryText),
        equalTo(DB_OPERATION, operationName),
        equalTo(DB_REDIS_DATABASE_INDEX, 1),
        equalTo(SERVER_ADDRESS, host),
        equalTo(SERVER_PORT, port),
        equalTo(maybeStablePeerService(), "test-peer-service"),
        equalTo(NETWORK_PEER_PORT, port),
        equalTo(NETWORK_PEER_ADDRESS, ip)
      };
    }
  }

  private static class BatchScenario {
    private final List<Request> requests;
    private final String operationName;
    private final String queryText;
    private final Long batchSize;

    private BatchScenario(Builder builder) {
      this.requests = builder.requests;
      this.operationName = builder.operationName;
      this.queryText = builder.queryText;
      this.batchSize = builder.batchSize;
    }

    private static Builder builder() {
      return new Builder();
    }

    private static class Builder {
      private List<Request> requests = new ArrayList<>();
      private String operationName;
      private String queryText;
      private Long batchSize;

      private Builder addRequest(Request request) {
        requests.add(request);
        return this;
      }

      private Builder requests(List<Request> requests) {
        this.requests = requests;
        return this;
      }

      private Builder operationName(String operationName) {
        this.operationName = operationName;
        return this;
      }

      private Builder queryText(String queryText) {
        this.queryText = queryText;
        return this;
      }

      private Builder batchSize(long batchSize) {
        this.batchSize = batchSize;
        return this;
      }

      private BatchScenario build() {
        return new BatchScenario(this);
      }
    }
  }
}
