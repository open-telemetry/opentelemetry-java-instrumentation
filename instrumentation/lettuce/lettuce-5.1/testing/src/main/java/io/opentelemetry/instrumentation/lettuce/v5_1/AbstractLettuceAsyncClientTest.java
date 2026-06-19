/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NetworkTypeValues.IPV4;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.google.common.collect.ImmutableMap;
import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings({"InterruptedExceptionSwallowed", "deprecation"}) // using deprecated semconv
public abstract class AbstractLettuceAsyncClientTest extends AbstractLettuceClientTest {
  private String dbUriNonExistent;
  private int incorrectPort;

  private static final ImmutableMap<String, String> TEST_HASH_MAP =
      ImmutableMap.of(
          "firstname", "John",
          "lastname", "Doe",
          "age", "53");

  private RedisAsyncCommands<String, String> asyncCommands;

  @BeforeAll
  void setUp() throws UnknownHostException {
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);
    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    incorrectPort = PortUtils.findOpenPort();
    dbUriNonExistent = "redis://" + host + ":" + incorrectPort + "/" + DB_INDEX;

    redisClient = createClient(embeddedDbUri);
    cleanup.deferAfterAll(redisClient::shutdown);
    redisClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS);

    connection = redisClient.connect();
    cleanup.deferAfterAll(connection);
    asyncCommands = connection.async();
    RedisCommands<String, String> syncCommands = connection.sync();

    syncCommands.set("TESTKEY", "TESTVAL");

    // 1 set trace
    testing().waitForTraces(1);
    testing().clearData();
  }

  boolean testCallback() {
    return true;
  }

  protected boolean connectHasSpans() {
    return false;
  }

  protected DeferredFlushSpanMode deferredFlushSpanMode() {
    return DeferredFlushSpanMode.BATCH;
  }

  @Test
  void testConnectUsingGetOnConnectionFuture() throws Exception {
    RedisClient testConnectionClient = RedisClient.create(embeddedDbUri);
    testConnectionClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS);

    ConnectionFuture<StatefulRedisConnection<String, String>> connectionFuture =
        testConnectionClient.connectAsync(
            StringCodec.UTF8, RedisURI.create("redis://" + host + ":" + port + "?timeout=3s"));
    StatefulRedisConnection<String, String> connection1 = connectionFuture.get();
    cleanup.deferCleanup(testConnectionClient::shutdown);
    cleanup.deferCleanup(connection1);

    assertThat(connection1).isNotNull();
    if (connectHasSpans()) {
      // ignore CLIENT SETINFO traces
      testing().waitForTraces(2);
    } else {
      // Lettuce tracing does not trace connect
      assertThat(testing().spans()).isEmpty();
    }
  }

  @Test
  void testConnectExceptionInsideTheConnectionFuture() {
    RedisClient testConnectionClient = RedisClient.create(dbUriNonExistent);
    testConnectionClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS);
    cleanup.deferCleanup(testConnectionClient::shutdown);

    Throwable thrown =
        catchThrowable(
            () -> {
              ConnectionFuture<StatefulRedisConnection<String, String>> connectionFuture =
                  testConnectionClient.connectAsync(
                      StringCodec.UTF8,
                      RedisURI.create("redis://" + host + ":" + incorrectPort + "?timeout=3s"));
              StatefulRedisConnection<String, String> connection1 = connectionFuture.get();
              cleanup.deferCleanup(connection1);
              assertThat(connection1).isNull();
            });

    assertThat(thrown).isInstanceOf(ExecutionException.class);

    // Lettuce tracing does not trace connect
    assertThat(testing().spans()).isEmpty();
  }

  @Test
  void testSetCommandUsingFutureGetWithTimeout() throws Exception {
    RedisFuture<String> redisFuture =
        testing().runWithSpan("parent", () -> asyncCommands.set("TESTSETKEY", "TESTSETVAL"));
    String res = redisFuture.get(3, SECONDS);

    assertThat(res).isEqualTo("OK");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName(spanName("SET"))
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), REDIS),
                                    equalTo(maybeStable(DB_STATEMENT), "SET TESTSETKEY ?"),
                                    equalTo(maybeStable(DB_OPERATION), "SET")))
                            .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));
  }

  @Test
  void testGetCommandChainedWithThenAccept() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    Consumer<String> consumer =
        res -> {
          if (testCallback()) {
            testing().runWithSpan("callback", () -> assertThat(res).isEqualTo("TESTVAL"));
          }
          future.complete(res);
        };

    testing()
        .runWithSpan(
            "parent",
            () -> {
              RedisFuture<String> redisFuture = asyncCommands.get("TESTKEY");
              redisFuture.thenAccept(consumer);
            });

    assertThat(future.get(10, SECONDS)).isEqualTo("TESTVAL");

    testing()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> spanAsserts =
                  new ArrayList<>(
                      asList(
                          span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                          span ->
                              span.hasName(spanName("GET"))
                                  .hasKind(SpanKind.CLIENT)
                                  .hasParent(trace.getSpan(0))
                                  .hasAttributesSatisfyingExactly(
                                      addExtraAttributes(
                                          equalTo(
                                              NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                                          equalTo(NETWORK_PEER_ADDRESS, ip),
                                          equalTo(NETWORK_PEER_PORT, port),
                                          equalTo(SERVER_ADDRESS, host),
                                          equalTo(SERVER_PORT, port),
                                          equalTo(maybeStable(DB_SYSTEM), REDIS),
                                          equalTo(maybeStable(DB_STATEMENT), "GET TESTKEY"),
                                          equalTo(maybeStable(DB_OPERATION), "GET")))
                                  .satisfies(
                                      AbstractLettuceClientTest::assertCommandEncodeEvents)));

              if (testCallback()) {
                spanAsserts.add(
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)));
              }
              trace.hasSpansSatisfyingExactly(spanAsserts);
            });
  }

  // to make sure instrumentation's chained completion stages won't interfere with user's, while
  // still recording spans
  @Test
  void testGetNonExistentKeyCommandWithHandleAsyncAndChainedWithThenApply() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    String successStr = "KEY MISSING";

    BiFunction<String, Throwable, String> firstStage =
        (res, error) -> {
          if (testCallback()) {
            testing()
                .runWithSpan(
                    "callback1",
                    () -> {
                      assertThat(res).isNull();
                      assertThat(error).isNull();
                    });
          }
          return (res == null ? successStr : res);
        };
    Function<String, Object> secondStage =
        input -> {
          if (testCallback()) {
            testing()
                .runWithSpan(
                    "callback2",
                    () -> {
                      assertThat(input).isEqualTo(successStr);
                    });
          }
          future.complete(successStr);
          return null;
        };

    testing()
        .runWithSpan(
            "parent",
            () -> {
              RedisFuture<String> redisFuture = asyncCommands.get("NON_EXISTENT_KEY");
              redisFuture.handleAsync(firstStage).thenApply(secondStage);
            });

    assertThat(future.get(10, SECONDS)).isEqualTo(successStr);

    testing()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> spanAsserts =
                  new ArrayList<>(
                      asList(
                          span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                          span ->
                              span.hasName(spanName("GET"))
                                  .hasKind(SpanKind.CLIENT)
                                  .hasParent(trace.getSpan(0))
                                  .hasAttributesSatisfyingExactly(
                                      addExtraAttributes(
                                          equalTo(
                                              NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                                          equalTo(NETWORK_PEER_ADDRESS, ip),
                                          equalTo(NETWORK_PEER_PORT, port),
                                          equalTo(SERVER_ADDRESS, host),
                                          equalTo(SERVER_PORT, port),
                                          equalTo(maybeStable(DB_SYSTEM), REDIS),
                                          equalTo(
                                              maybeStable(DB_STATEMENT), "GET NON_EXISTENT_KEY"),
                                          equalTo(maybeStable(DB_OPERATION), "GET")))
                                  .satisfies(
                                      AbstractLettuceClientTest::assertCommandEncodeEvents)));

              if (testCallback()) {
                spanAsserts.addAll(
                    asList(
                        span ->
                            span.hasName("callback1")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParent(trace.getSpan(0)),
                        span ->
                            span.hasName("callback2")
                                .hasKind(SpanKind.INTERNAL)
                                .hasParent(trace.getSpan(0))));
              }
              trace.hasSpansSatisfyingExactly(spanAsserts);
            });
  }

  @Test
  void testCommandWithNoArgumentsUsingBiconsumer() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    BiConsumer<String, Throwable> biConsumer =
        (keyRetrieved, error) -> {
          if (testCallback()) {
            testing()
                .runWithSpan(
                    "callback",
                    () -> {
                      assertThat(keyRetrieved).isNotNull();
                    });
          }
          future.complete(keyRetrieved);
        };

    testing()
        .runWithSpan(
            "parent",
            () -> {
              RedisFuture<String> redisFuture = asyncCommands.randomkey();
              redisFuture.whenCompleteAsync(biConsumer);
            });

    assertThat(future.get(10, SECONDS)).isNotNull();

    testing()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> spanAsserts =
                  new ArrayList<>(
                      asList(
                          span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                          span ->
                              span.hasName(spanName("RANDOMKEY"))
                                  .hasKind(SpanKind.CLIENT)
                                  .hasParent(trace.getSpan(0))
                                  .hasAttributesSatisfyingExactly(
                                      addExtraAttributes(
                                          equalTo(
                                              NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                                          equalTo(NETWORK_PEER_ADDRESS, ip),
                                          equalTo(NETWORK_PEER_PORT, port),
                                          equalTo(SERVER_ADDRESS, host),
                                          equalTo(SERVER_PORT, port),
                                          equalTo(maybeStable(DB_SYSTEM), REDIS),
                                          equalTo(maybeStable(DB_STATEMENT), "RANDOMKEY"),
                                          equalTo(maybeStable(DB_OPERATION), "RANDOMKEY")))
                                  .satisfies(
                                      AbstractLettuceClientTest::assertCommandEncodeEvents)));

              if (testCallback()) {
                spanAsserts.add(
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0)));
              }
              trace.hasSpansSatisfyingExactly(spanAsserts);
            });
  }

  @ParameterizedTest
  @MethodSource("deferredFlushScenarios")
  void deferredFlushCommand(BatchScenario scenario) throws Exception {
    StatefulRedisConnection<String, String> statefulConnection =
        asyncCommands.getStatefulConnection();
    statefulConnection.setAutoFlushCommands(false);
    cleanup.deferCleanup(() -> statefulConnection.setAutoFlushCommands(true));

    List<RedisFuture<?>> futures = new ArrayList<>();
    for (BatchCommand command : scenario.commands) {
      futures.add(command.run(asyncCommands));
    }
    statefulConnection.flushCommands();
    for (RedisFuture<?> future : futures) {
      future.get(10, SECONDS);
    }

    if (scenario.isEmpty()) {
      assertThat(testing().spans()).isEmpty();
      return;
    }

    if (deferredFlushSpanMode() == DeferredFlushSpanMode.BATCH) {
      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName(scenario.operationName))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(maybeStable(DB_SYSTEM), REDIS),
                                      equalTo(maybeStable(DB_STATEMENT), scenario.queryText),
                                      equalTo(maybeStable(DB_OPERATION), scenario.operationName),
                                      equalTo(
                                          DB_OPERATION_BATCH_SIZE,
                                          emitStableDatabaseSemconv()
                                              ? scenario.batchSize
                                              : null)))));
      return;
    }

    // PER_COMMAND is the span mode emitted by library instrumentation: Lettuce's tracing API
    // starts each command span as the command is written, so auto-flush batches cannot be
    // represented as a single aggregate span there. This is not the ideal shape, but documents the
    // current library behavior.
    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    for (OldExpectedCommand command : scenario.oldExpectedCommands) {
      assertions.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(spanName(command.operationName))
                          .hasKind(SpanKind.CLIENT)
                          .hasAttributesSatisfyingExactly(
                              addExtraAttributes(
                                  equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                                  equalTo(NETWORK_PEER_ADDRESS, ip),
                                  equalTo(NETWORK_PEER_PORT, port),
                                  equalTo(SERVER_ADDRESS, host),
                                  equalTo(SERVER_PORT, port),
                                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                                  equalTo(maybeStable(DB_STATEMENT), command.queryText),
                                  equalTo(maybeStable(DB_OPERATION), command.operationName),
                                  equalTo(DB_OPERATION_BATCH_SIZE, null)))
                          .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));
    }
    testing().waitAndAssertTraces(assertions);
  }

  private static Stream<Arguments> deferredFlushScenarios() {
    return Stream.of(
        Arguments.argumentSet("empty", BatchScenario.builder().build()),
        Arguments.argumentSet(
            "single",
            BatchScenario.builder()
                .addCommand(commands -> commands.set("batch1", "v1"))
                .operationName("SET")
                .queryText("SET batch1 ?")
                .addOldExpectedCommand("SET", "SET batch1 ?")
                .build()),
        Arguments.argumentSet(
            "twoSameOperation",
            BatchScenario.builder()
                .addCommand(commands -> commands.set("batch1", "v1"))
                .addCommand(commands -> commands.set("batch2", "v2"))
                .operationName("PIPELINE SET")
                .queryText("SET batch1 ?;SET batch2 ?")
                .batchSize(2)
                .addOldExpectedCommand("SET", "SET batch1 ?")
                .addOldExpectedCommand("SET", "SET batch2 ?")
                .build()),
        Arguments.argumentSet(
            "twoDifferentOperations",
            BatchScenario.builder()
                .addCommand(commands -> commands.set("batch1", "v1"))
                .addCommand(commands -> commands.get("batch1"))
                .operationName("PIPELINE")
                .queryText("SET batch1 ?;GET batch1")
                .batchSize(2)
                .addOldExpectedCommand("SET", "SET batch1 ?")
                .addOldExpectedCommand("GET", "GET batch1")
                .build()));
  }

  private static class BatchScenario {
    private final List<BatchCommand> commands;
    private final String operationName;
    private final String queryText;
    private final Long batchSize;
    private final List<OldExpectedCommand> oldExpectedCommands;

    private BatchScenario(
        List<BatchCommand> commands,
        String operationName,
        String queryText,
        Long batchSize,
        List<OldExpectedCommand> oldExpectedCommands) {
      this.commands = commands;
      this.operationName = operationName;
      this.queryText = queryText;
      this.batchSize = batchSize;
      this.oldExpectedCommands = oldExpectedCommands;
    }

    private static Builder builder() {
      return new Builder();
    }

    private boolean isEmpty() {
      return commands.isEmpty();
    }

    private static class Builder {
      private final List<BatchCommand> commands = new ArrayList<>();
      private String operationName;
      private String queryText;
      private Long batchSize;
      private final List<OldExpectedCommand> oldExpectedCommands = new ArrayList<>();

      private Builder addCommand(BatchCommand command) {
        commands.add(command);
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

      private Builder addOldExpectedCommand(String operationName, String queryText) {
        oldExpectedCommands.add(new OldExpectedCommand(operationName, queryText));
        return this;
      }

      private BatchScenario build() {
        return new BatchScenario(
            commands, operationName, queryText, batchSize, oldExpectedCommands);
      }
    }
  }

  private interface BatchCommand {
    RedisFuture<?> run(RedisAsyncCommands<String, String> commands);
  }

  private static class OldExpectedCommand {
    private final String operationName;
    private final String queryText;

    private OldExpectedCommand(String operationName, String queryText) {
      this.operationName = operationName;
      this.queryText = queryText;
    }
  }

  protected enum DeferredFlushSpanMode {
    BATCH,
    PER_COMMAND
  }

  @Test
  void testHashSetAndThenNestApplyToHashGetall() throws Exception {
    CompletableFuture<Map<String, String>> future = new CompletableFuture<>();

    RedisFuture<String> hmsetFuture = asyncCommands.hmset("TESTHM", TEST_HASH_MAP);
    hmsetFuture.thenApplyAsync(
        setResult -> {
          // Wait for 'hmset' trace to get written
          testing().waitForTraces(1);

          if (!"OK".equals(setResult)) {
            future.completeExceptionally(new AssertionError("Wrong hmset result " + setResult));
            return null;
          }

          RedisFuture<Map<String, String>> hmGetAllFuture = asyncCommands.hgetall("TESTHM");
          hmGetAllFuture.whenComplete(
              (result, exception) -> {
                if (exception != null) {
                  future.completeExceptionally(exception);
                } else {
                  future.complete(result);
                }
              });
          return null;
        });

    assertThat(future.get(10, SECONDS)).isEqualTo(TEST_HASH_MAP);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("HMSET"))
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), REDIS),
                                    equalTo(
                                        maybeStable(DB_STATEMENT),
                                        "HMSET TESTHM firstname ? lastname ? age ?"),
                                    equalTo(maybeStable(DB_OPERATION), "HMSET")))
                            .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("HGETALL"))
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), REDIS),
                                    equalTo(maybeStable(DB_STATEMENT), "HGETALL TESTHM"),
                                    equalTo(maybeStable(DB_OPERATION), "HGETALL")))
                            .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));
  }
}
