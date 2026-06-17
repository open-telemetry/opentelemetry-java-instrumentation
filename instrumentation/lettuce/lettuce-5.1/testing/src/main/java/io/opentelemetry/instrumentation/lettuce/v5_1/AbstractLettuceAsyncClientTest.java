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
import static java.util.Collections.emptyList;
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

  protected boolean aggregateDeferredFlush() {
    return false;
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

  @ParameterizedTest(name = "{0}")
  @MethodSource("deferredFlushScenarios")
  void deferredFlushCommand(
      String name, AsyncCommandsScenario scenario, List<ExpectedCommand> expectedCommands)
      throws Exception {
    asyncCommands.setAutoFlushCommands(false);
    cleanup.deferCleanup(() -> asyncCommands.setAutoFlushCommands(true));

    List<RedisFuture<?>> futures = scenario.run(asyncCommands);
    asyncCommands.flushCommands();
    for (RedisFuture<?> future : futures) {
      future.get(10, SECONDS);
    }

    if (expectedCommands.isEmpty()) {
      assertThat(testing().spans()).isEmpty();
      return;
    }

    if (aggregateDeferredFlush()) {
      String operation = pipelineOperation(expectedCommands);
      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName(operation))
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
                                          pipelineStatement(expectedCommands)),
                                      equalTo(maybeStable(DB_OPERATION), operation),
                                      equalTo(
                                          DB_OPERATION_BATCH_SIZE,
                                          emitStableDatabaseSemconv() && expectedCommands.size() > 1
                                              ? (long) expectedCommands.size()
                                              : null)))));
      return;
    }

    List<Consumer<TraceAssert>> assertions = new ArrayList<>();
    for (ExpectedCommand command : expectedCommands) {
      ExpectedCommand expected = command;
      assertions.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(spanName(expected.operation))
                          .hasKind(SpanKind.CLIENT)
                          .hasAttributesSatisfyingExactly(
                              addExtraAttributes(
                                  equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                                  equalTo(NETWORK_PEER_ADDRESS, ip),
                                  equalTo(NETWORK_PEER_PORT, port),
                                  equalTo(SERVER_ADDRESS, host),
                                  equalTo(SERVER_PORT, port),
                                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                                  equalTo(maybeStable(DB_STATEMENT), expected.statement),
                                  equalTo(maybeStable(DB_OPERATION), expected.operation),
                                  equalTo(DB_OPERATION_BATCH_SIZE, null)))
                          .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));
    }
    testing().waitAndAssertTraces(assertions);
  }

  private static String pipelineOperation(List<ExpectedCommand> commands) {
    if (commands.size() == 1) {
      return commands.get(0).operation;
    }
    String operation = commands.get(0).operation;
    for (ExpectedCommand command : commands) {
      if (!operation.equals(command.operation)) {
        return "PIPELINE";
      }
    }
    return "PIPELINE " + operation;
  }

  private static String pipelineStatement(List<ExpectedCommand> commands) {
    StringBuilder statement = new StringBuilder();
    for (ExpectedCommand command : commands) {
      if (statement.length() > 0) {
        statement.append(';');
      }
      statement.append(command.statement);
    }
    return statement.toString();
  }

  private static Stream<Arguments> deferredFlushScenarios() {
    return Stream.of(
        Arguments.of("empty", (AsyncCommandsScenario) commands -> emptyList(), emptyList()),
        Arguments.of(
            "single",
            (AsyncCommandsScenario) commands -> futures(commands.set("batch1", "v1")),
            expectedCommands(expectedCommand("SET", "SET batch1 ?"))),
        Arguments.of(
            "twoSameOperation",
            (AsyncCommandsScenario)
                commands -> futures(commands.set("batch1", "v1"), commands.set("batch2", "v2")),
            expectedCommands(
                expectedCommand("SET", "SET batch1 ?"), expectedCommand("SET", "SET batch2 ?"))),
        Arguments.of(
            "twoDifferentOperations",
            (AsyncCommandsScenario)
                commands -> futures(commands.set("batch1", "v1"), commands.get("batch1")),
            expectedCommands(
                expectedCommand("SET", "SET batch1 ?"), expectedCommand("GET", "GET batch1"))));
  }

  private static List<RedisFuture<?>> futures(RedisFuture<?>... futures) {
    return asList(futures);
  }

  private static List<ExpectedCommand> expectedCommands(ExpectedCommand... commands) {
    return asList(commands);
  }

  private static ExpectedCommand expectedCommand(String operation, String statement) {
    return new ExpectedCommand(operation, statement);
  }

  private interface AsyncCommandsScenario {
    List<RedisFuture<?>> run(RedisAsyncCommands<String, String> commands);
  }

  private static class ExpectedCommand {
    private final String operation;
    private final String statement;

    private ExpectedCommand(String operation, String statement) {
      this.operation = operation;
      this.statement = statement;
    }
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
