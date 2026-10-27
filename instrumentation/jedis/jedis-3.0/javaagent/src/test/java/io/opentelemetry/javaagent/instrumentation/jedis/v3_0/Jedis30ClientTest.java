/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.assertj.core.api.AbstractLongAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Builder;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

@SuppressWarnings("deprecation") // using deprecated semconv
class Jedis30ClientTest {
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
    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    jedis = new Jedis(host, port);
    cleanup.deferAfterAll(jedis);
  }

  @BeforeEach
  void reset() {
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
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET foo ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(maybeStablePeerService(), testing.expectedPeerService()),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.jedis-3.0",
        DB_OPERATION_NAME,
        DB_SYSTEM_NAME,
        DB_NAMESPACE,
        SERVER_ADDRESS,
        SERVER_PORT,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT);
  }

  @Test
  void wrongTypeErrorType() {
    jedis.lpush("wrong-type", "value");
    assertLpushTrace();
    testing.clearData();

    assertThatThrownBy(() -> jedis.get("wrong-type")).hasMessageStartingWith("WRONGTYPE");

    assertErrorType("WRONGTYPE");
  }

  @Test
  void dedicatedServerErrorUsesExceptionClass() {
    Throwable error =
        catchThrowable(
            () -> jedis.eval("return redis.error_reply(ARGV[1])", 0, "MOVED 42 127.0.0.1:6379"));

    assertThat(error).isInstanceOf(JedisDataException.class);
    assertThat(error.getClass()).isNotEqualTo(JedisDataException.class);
    assertErrorType(error.getClass().getName());
  }

  @Test
  void unreliableServerPrefixUsesExceptionClass() {
    Throwable error =
        catchThrowable(() -> jedis.eval("return redis.error_reply(ARGV[1])", 0, "échec details"));

    assertThat(error).isInstanceOf(JedisDataException.class);
    assertErrorType(error.getClass().getName());
  }

  @Test
  void clientFailureUsesExceptionClassAndDoesNotLeak() {
    Jedis broken = new Jedis("127.0.0.1", 0);
    cleanup.deferCleanup(broken::disconnect);

    Throwable error = catchThrowable(() -> broken.get("key"));

    assertThat(error).isInstanceOf(JedisConnectionException.class);
    assertErrorType(error.getClass().getName());

    testing.clearData();
    jedis.set("after-error", "value");
    assertErrorType(null);
  }

  @Test
  void pipelineAndTransactionErrorsCompleteBatchSpans() {
    jedis.lpush("wrong-type", "value");
    jedis.set("not-number", "value");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("LPUSH " + host + ":" + port)),
        trace -> trace.hasSpansSatisfyingExactly(span -> span.hasName("SET " + host + ":" + port)));
    testing.clearData();

    Pipeline pipeline = jedis.pipelined();
    pipeline.get("wrong-type");
    pipeline.incr("not-number");
    assertThat(pipeline.syncAndReturnAll())
        .hasSize(2)
        .allSatisfy(result -> assertThat(result).isInstanceOf(JedisDataException.class));
    assertErrorType("WRONGTYPE");

    testing.clearData();
    Transaction transaction = jedis.multi();
    transaction.get("wrong-type");
    transaction.incr("not-number");
    assertThat(transaction.exec())
        .hasSize(2)
        .allSatisfy(result -> assertThat(result).isInstanceOf(JedisDataException.class));
    assertErrorType("WRONGTYPE");
  }

  @Test
  void terminalTransactionServerFailureWinsOverQueuedError() {
    Jedis client = new Jedis(host, port);
    cleanup.deferCleanup(client::disconnect);
    Transaction transaction = client.multi();
    String[] noKeys = {};
    transaction.del(noKeys);
    testing.clearData();

    Throwable error = catchThrowable(transaction::exec);
    assertThat(error).isInstanceOf(JedisDataException.class);
    assertThat(error.getMessage()).startsWith("EXECABORT");
    assertErrorType(
        error.getClass() == JedisDataException.class ? "EXECABORT" : error.getClass().getName());

    testing.clearData();
    jedis.set("after-error", "value");
    assertErrorType(null);
  }

  @Test
  void terminalPipelineConnectionFailureWinsOverServerReply() {
    Jedis client = new Jedis(host, port);
    cleanup.deferCleanup(client::disconnect);
    client.lpush("wrong-type", "value");
    assertLpushTrace();

    Pipeline pipeline = client.pipelined();
    pipeline.get("wrong-type");
    client.getClient().quit();
    pipeline.get("after-quit");
    pipeline.get("after-quit-again");
    testing.clearData();

    Throwable error = catchThrowable(pipeline::syncAndReturnAll);
    assertThat(error).isInstanceOf(JedisConnectionException.class);
    assertErrorType(error.getClass().getName());

    testing.clearData();
    jedis.set("after-error", "value");
    assertErrorType(null);
  }

  @Test
  void nestedMultiServerErrorDoesNotFailSuccessfulOuterPipeline() {
    String username = "otel-no-multi";
    jedis
        .getClient()
        .sendCommand(
            () -> "ACL".getBytes(UTF_8),
            "SETUSER",
            username,
            "on",
            "nopass",
            "~*",
            "+@all",
            "-multi");
    assertThat(jedis.getClient().getStatusCodeReply()).isEqualTo("OK");

    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    nestedClient.getClient().sendCommand(Protocol.Command.AUTH, username, "unused-password");
    assertThat(nestedClient.getClient().getStatusCodeReply()).isEqualTo("OK");
    testing.clearData();

    NestedMultiPipeline outer = new NestedMultiPipeline(nestedClient);
    outer.setClient(jedis.getClient());
    outer.set("outer-pipeline", "value");

    assertThat(outer.syncAndReturnAll()).containsExactly("OK");
    assertThat(outer.nestedMultiError).hasMessageStartingWith("NOPERM");
    assertErrorType(null);
  }

  private static class NestedMultiPipeline extends Pipeline {
    private final Jedis nestedClient;
    private JedisDataException nestedMultiError;

    private NestedMultiPipeline(Jedis nestedClient) {
      this.nestedClient = nestedClient;
    }

    @Override
    protected <T> Response<T> getResponse(Builder<T> builder) {
      return super.getResponse(
          new Builder<T>() {
            @Override
            public T build(Object response) {
              try {
                nestedClient.multi();
              } catch (JedisDataException e) {
                nestedMultiError = e;
              }
              return builder.build(response);
            }
          });
    }
  }

  private static void assertLpushTrace() {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(span -> span.hasName("LPUSH " + host + ":" + port)));
  }

  private static void assertErrorType(String errorType) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasAttributesSatisfying(equalTo(ERROR_TYPE, errorType))));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-3.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .anySatisfy(
                            point ->
                                assertThat(point.getAttributes().get(ERROR_TYPE))
                                    .isEqualTo(errorType))));
  }

  @Test
  void pooledCommand() {
    JedisPool pool = new JedisPool(host, port);
    cleanup.deferCleanup(pool);
    try (Jedis pooled = pool.getResource()) {
      pooled.set("pooled", "value");
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET pooled ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(maybeStablePeerService(), testing.expectedPeerService()),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative))));
  }

  @Test
  void directHostAndPortCommandUsesOriginalTarget() {
    HostAndPort endpoint = HostAndPort.parseString("localhost:" + port);
    try (Jedis direct = new Jedis(endpoint)) {
      direct.set("direct-host-and-port", "value");
    }

    assertHostAndPortTarget();
  }

  private static void assertHostAndPortTarget() {
    testing.waitForTraces(1);
    assertThat(testing.spans())
        .singleElement()
        .satisfies(
            span -> {
              assertThat(span.getAttributes().get(SERVER_ADDRESS)).isEqualTo("localhost");
              assertThat(span.getAttributes().get(SERVER_PORT)).isEqualTo((long) port);
            });
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
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET foo ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(maybeStablePeerService(), testing.expectedPeerService()),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "GET foo"),
                            equalTo(DB_OPERATION_NAME, "GET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(maybeStablePeerService(), testing.expectedPeerService()),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative))));
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
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET foo ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(maybeStablePeerService(), testing.expectedPeerService()),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("RANDOMKEY " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "RANDOMKEY"),
                            equalTo(DB_OPERATION_NAME, "RANDOMKEY"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(maybeStablePeerService(), testing.expectedPeerService()),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative))));
  }

  @Test
  void nonDefaultDatabaseIndex() {
    jedis.select(1);
    jedis.set("foo", "bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SELECT 1"),
                            equalTo(DB_OPERATION_NAME, "SELECT"),
                            // jedis 3.x updates the database index only after SELECT succeeds, so
                            // this span still reports the database it was sent on
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(maybeStablePeerService(), testing.expectedPeerService()),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET foo ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "1"),
                            equalTo(maybeStablePeerService(), testing.expectedPeerService()),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative))));
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
                    span.hasName(scenario.operationName + " " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, scenario.queryText),
                            equalTo(DB_OPERATION_NAME, scenario.operationName),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_OPERATION_BATCH_SIZE, scenario.batchSize),
                            equalTo(maybeStablePeerService(), testing.expectedPeerService()),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative))));
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
                    span.hasName(scenario.operationName + " " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, scenario.queryText),
                            equalTo(DB_OPERATION_NAME, scenario.operationName),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_OPERATION_BATCH_SIZE, scenario.batchSize),
                            equalTo(maybeStablePeerService(), testing.expectedPeerService()),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            satisfies(NETWORK_PEER_PORT, AbstractLongAssert::isNotNegative))));
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
                .queryText("SET batch1 ?; SET batch2 ?")
                .batchSize(2)
                .build()),
        argumentSet(
            "twoDifferentOperations",
            BatchScenario.<Pipeline>builder()
                .addCommand(pipeline -> pipeline.set("batch1", "v1"))
                .addCommand(pipeline -> pipeline.get("batch1"))
                .operationName("PIPELINE")
                .queryText("SET batch1 ?; GET batch1")
                .batchSize(2)
                .build()));
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
                .queryText("SET tx1 ?; SET tx2 ?")
                .batchSize(2)
                .build()),
        argumentSet(
            "twoDifferentOperations",
            BatchScenario.<Transaction>builder()
                .addCommand(transaction -> transaction.set("tx1", "v1"))
                .addCommand(transaction -> transaction.get("tx1"))
                .operationName("MULTI")
                .queryText("SET tx1 ?; GET tx1")
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
