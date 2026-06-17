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
import static java.util.concurrent.TimeUnit.SECONDS;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;

@SuppressWarnings("deprecation") // using deprecated semconv
class VertxRedisClientTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);
  private static String host;
  private static String ip;
  private static int port;
  private static Vertx vertx;
  private static Redis client;
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
    RedisConnection connection =
        client.connect().toCompletionStage().toCompletableFuture().get(30, SECONDS);
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("batchScenarios")
  void batchCommand(BatchScenario scenario) throws Exception {
    testing.clearData();

    client.batch(scenario.requests).toCompletionStage().toCompletableFuture().get(30, SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? scenario.operation + " " + host + ":" + port
                                : scenario.operation)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            redisSpanAttributes(
                                scenario.operation, scenario.statement, scenario.batchSize))));
  }

  private static Stream<BatchScenario> batchScenarios() {
    return Stream.of(
        BatchScenario.builder("single")
            .requests(Request.cmd(Command.SET).arg("batch1").arg("v1"))
            .operation("SET")
            .statement("SET batch1 ?")
            .build(),
        BatchScenario.builder("twoSameOperation")
            .requests(
                Request.cmd(Command.SET).arg("batch1").arg("v1"),
                Request.cmd(Command.SET).arg("batch2").arg("v2"))
            .operation("PIPELINE SET")
            .statement("SET batch1 ?;SET batch2 ?")
            .batchSize(2)
            .build(),
        BatchScenario.builder("twoDifferentOperations")
            .requests(
                Request.cmd(Command.SET).arg("batch1").arg("v1"),
                Request.cmd(Command.GET).arg("batch1"))
            .operation("PIPELINE")
            .statement("SET batch1 ?;GET batch1")
            .batchSize(2)
            .build());
  }

  private static AttributeAssertion[] redisSpanAttributes(String operation, String queryText) {
    return redisSpanAttributes(operation, queryText, null);
  }

  private static AttributeAssertion[] redisSpanAttributes(
      String operation, String queryText, Long batchSize) {
    // not testing database/dup
    if (emitStableDatabaseSemconv()) {
      return new AttributeAssertion[] {
        equalTo(DB_SYSTEM_NAME, REDIS),
        equalTo(DB_QUERY_TEXT, queryText),
        equalTo(DB_OPERATION_NAME, operation),
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
        equalTo(DB_OPERATION, operation),
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
    private final String name;
    private final List<Request> requests;
    private final String operation;
    private final String statement;
    private final Long batchSize;

    private BatchScenario(
        String name, List<Request> requests, String operation, String statement, Long batchSize) {
      this.name = name;
      this.requests = requests;
      this.operation = operation;
      this.statement = statement;
      this.batchSize = batchSize;
    }

    private static Builder builder(String name) {
      return new Builder(name);
    }

    @Override
    public String toString() {
      return name;
    }

    private static class Builder {
      private final String name;
      private List<Request> requests;
      private String operation;
      private String statement;
      private Long batchSize;

      private Builder(String name) {
        this.name = name;
      }

      private Builder requests(Request... requests) {
        this.requests = asList(requests);
        return this;
      }

      private Builder operation(String operation) {
        this.operation = operation;
        return this;
      }

      private Builder statement(String statement) {
        this.statement = statement;
        return this;
      }

      private Builder batchSize(long batchSize) {
        this.batchSize = batchSize;
        return this;
      }

      private BatchScenario build() {
        return new BatchScenario(name, requests, operation, statement, batchSize);
      }
    }
  }
}
