/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NetworkTypeValues.IPV4;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Transaction;

@SuppressWarnings("deprecation") // using deprecated semconv
class Jedis40ClientTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private static String host;

  private static String ip;

  private static int port;

  private static Jedis jedis;

  @BeforeAll
  static void setup() throws UnknownHostException {
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);
    port = redisServer.getMappedPort(6379);
    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    jedis = new Jedis(host, port);
    cleanup.deferAfterAll(jedis);
  }

  @BeforeEach
  void reset() {
    // jedis 4.x leaves the connection "in multi" after multi()/exec() (Transaction has no
    // back-reference to clear Jedis state), so reset it before reusing the shared client.
    jedis.resetState();
    jedis.select(0);
    jedis.flushAll();
    testing.clearData();
  }

  @Test
  void setCommand() {
    jedis.set("foo", "bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.jedis-4.0",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_NAMESPACE,
        SERVER_ADDRESS,
        SERVER_PORT,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT);
  }

  @Test
  void getCommand() {
    jedis.set("foo", "bar");
    String value = jedis.get("foo");

    assertThat(value).isEqualTo("bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "GET foo"),
                            equalTo(maybeStable(DB_OPERATION), "GET"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @Test
  void commandWithNoArguments() {
    jedis.set("foo", "bar");
    String value = jedis.randomKey();

    assertThat(value).isEqualTo("foo");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "RANDOMKEY " + host + ":" + port
                                : "RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "RANDOMKEY"),
                            equalTo(maybeStable(DB_OPERATION), "RANDOMKEY"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @Test
  void selectCommandDoesNotChangeEstablishedDatabaseIndex() {
    jedis.select(1);
    jedis.set("foo", "bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv() ? "SELECT " + host + ":" + port : "SELECT")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SELECT 1"),
                            equalTo(maybeStable(DB_OPERATION), "SELECT"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @Test
  void configuredDatabaseIndex() {
    try (Jedis databaseOne =
        new Jedis(
            new HostAndPort(host, port), DefaultJedisClientConfig.builder().database(1).build())) {
      testing.clearData();
      databaseOne.set("foo", "bar");
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "1" : null),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @ParameterizedTest
  @MethodSource("batchScenarios")
  void pipelineCommand(BatchScenario<Pipeline> scenario) {
    Pipeline pipeline = jedis.pipelined();
    scenario.commands.accept(pipeline);
    pipeline.sync();

    if (scenario.operationName == null) {
      assertThat(testing.spans()).isEmpty();
      return;
    }

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
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), scenario.queryText),
                            equalTo(maybeStable(DB_OPERATION), scenario.operationName),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? scenario.batchSize : null),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  private static Stream<Arguments> batchScenarios() {
    return Stream.of(
        // no span is created for empty pipelines
        argumentSet("empty", BatchScenario.<Pipeline>builder().build()),
        argumentSet(
            "single",
            BatchScenario.<Pipeline>builder()
                .addCommand(pipeline -> pipeline.set("batch1", "v1"))
                .operationName("SET")
                .queryText("SET batch1 ?")
                .build()),
        argumentSet(
            "twoSameOperation",
            BatchScenario.<Pipeline>builder()
                .addCommand(pipeline -> pipeline.set("batch1", "v1"))
                .addCommand(pipeline -> pipeline.set("batch2", "v2"))
                .operationName("PIPELINE SET")
                .queryText(
                  emitStableDatabaseSemconv()
                    ? "SET batch1 ?; SET batch2 ?"
                    : "SET batch1 ?;SET batch2 ?")
                .batchSize(2)
                .build()),
        argumentSet(
            "twoDifferentOperations",
            BatchScenario.<Pipeline>builder()
                .addCommand(pipeline -> pipeline.set("batch1", "v1"))
                .addCommand(pipeline -> pipeline.get("batch1"))
                .operationName("PIPELINE")
                .queryText(
                  emitStableDatabaseSemconv()
                    ? "SET batch1 ?; GET batch1"
                    : "SET batch1 ?;GET batch1")
                .batchSize(2)
                .build()));
  }

  @ParameterizedTest
  @MethodSource("transactionScenarios")
  void transactionCommand(BatchScenario<Transaction> scenario) {
    // A MULTI/EXEC transaction is reported as a single batch span (db.operation.name "MULTI"),
    // mirroring how a pipeline is reported as "PIPELINE". The framing MULTI/EXEC commands and the
    // individual queued commands do not get their own spans.
    Transaction transaction = jedis.multi();
    scenario.commands.accept(transaction);
    transaction.exec();

    if (scenario.operationName == null) {
      assertThat(testing.spans()).isEmpty();
      return;
    }

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
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), scenario.queryText),
                            equalTo(maybeStable(DB_OPERATION), scenario.operationName),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? scenario.batchSize : null),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @Test
  void transactionDiscardEmitsNoSpans() {
    // A discarded transaction is abandoned: its queued commands are dropped and the framing
    // MULTI/DISCARD commands do not produce any spans.
    Transaction transaction = jedis.multi();
    transaction.set("tx1", "v1");
    transaction.get("tx1");
    transaction.discard();

    assertThat(testing.spans()).isEmpty();
  }

  private static Stream<Arguments> transactionScenarios() {
    return Stream.of(
        // no span is created for empty transactions
        argumentSet("empty", BatchScenario.<Transaction>builder().build()),
        argumentSet(
            "single",
            BatchScenario.<Transaction>builder()
                .addCommand(transaction -> transaction.set("tx1", "v1"))
                .operationName("SET")
                .queryText("SET tx1 ?")
                .build()),
        argumentSet(
            "twoSameOperation",
            BatchScenario.<Transaction>builder()
                .addCommand(transaction -> transaction.set("tx1", "v1"))
                .addCommand(transaction -> transaction.set("tx2", "v2"))
                .operationName("MULTI SET")
                .queryText(
                  emitStableDatabaseSemconv()
                    ? "SET tx1 ?; SET tx2 ?"
                    : "SET tx1 ?;SET tx2 ?")
                .batchSize(2)
                .build()),
        argumentSet(
            "twoDifferentOperations",
            BatchScenario.<Transaction>builder()
                .addCommand(transaction -> transaction.set("tx1", "v1"))
                .addCommand(transaction -> transaction.get("tx1"))
                .operationName("MULTI")
                .queryText(
                    emitStableDatabaseSemconv()
                        ? "SET tx1 ?; GET tx1"
                        : "SET tx1 ?;GET tx1")
                .batchSize(2)
                .build()));
  }

  private static class BatchScenario<T> {
    private final Consumer<T> commands;
    private final String operationName;
    private final String queryText;
    private final Long batchSize;

    private BatchScenario(Builder<T> builder) {
      this.commands = builder.commands;
      this.operationName = builder.operationName;
      this.queryText = builder.queryText;
      this.batchSize = builder.batchSize;
    }

    private static <T> Builder<T> builder() {
      return new Builder<>();
    }

    private static class Builder<T> {
      private Consumer<T> commands = command -> {};
      private String operationName;
      private String queryText;
      private Long batchSize;

      private Builder<T> addCommand(Consumer<T> command) {
        this.commands = this.commands.andThen(command);
        return this;
      }

      private Builder<T> operationName(String operationName) {
        this.operationName = operationName;
        return this;
      }

      private Builder<T> queryText(String queryText) {
        this.queryText = queryText;
        return this;
      }

      private Builder<T> batchSize(long batchSize) {
        this.batchSize = batchSize;
        return this;
      }

      private BatchScenario<T> build() {
        return new BatchScenario<>(this);
      }
    }
  }
}
