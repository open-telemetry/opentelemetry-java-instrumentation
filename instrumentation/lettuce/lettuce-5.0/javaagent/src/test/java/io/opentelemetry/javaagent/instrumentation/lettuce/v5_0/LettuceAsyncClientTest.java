/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.ExperimentalHelper.experimental;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import com.google.common.collect.ImmutableMap;
import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.protocol.AsyncCommand;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@SuppressWarnings("deprecation") // using deprecated semconv
class LettuceAsyncClientTest extends AbstractLettuceClientTest {
  private int incorrectPort;
  private String dbUriNonExistent;

  private static final ImmutableMap<String, String> testHashMap =
      ImmutableMap.of(
          "firstname", "John",
          "lastname", "Doe",
          "age", "53");

  private static final int NON_DEFAULT_DB_INDEX = 1;

  private RedisAsyncCommands<String, String> asyncCommands;
  private RedisClient nonDefaultDbClient;
  private StatefulRedisConnection<String, String> nonDefaultDbConnection;
  private RedisAsyncCommands<String, String> nonDefaultDbCommands;

  @BeforeAll
  void setUp() throws UnknownHostException {
    redisServer.start();

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    incorrectPort = PortUtils.findOpenPort();
    dbUriNonExistent = "redis://" + host + ":" + incorrectPort + "/" + DB_INDEX;

    redisClient = RedisClient.create(embeddedDbUri);
    redisClient.setOptions(CLIENT_OPTIONS);

    connection = redisClient.connect();
    asyncCommands = connection.async();
    RedisCommands<String, String> syncCommands = connection.sync();

    nonDefaultDbClient =
        RedisClient.create("redis://" + host + ":" + port + "/" + NON_DEFAULT_DB_INDEX);
    nonDefaultDbClient.setOptions(CLIENT_OPTIONS);
    nonDefaultDbConnection = nonDefaultDbClient.connect();
    nonDefaultDbCommands = nonDefaultDbConnection.async();

    syncCommands.set("TESTKEY", "TESTVAL");

    // Lettuce 5 emits SET plus SELECT while opening the non-default database.
    // Lettuce 6+ performs the selection as an activation command without a command span.
    boolean lettuce5 = isLettuce5();
    int expectedTraceCount = lettuce5 ? 2 : 1;
    if (connectionTelemetryEnabled()) {
      expectedTraceCount += 2;
      if (lettuce5) {
        // SELECT is a child of the second CONNECT span instead of starting another trace.
        expectedTraceCount--;
      }
    }
    testing.waitForTraces(expectedTraceCount);
  }

  @AfterAll
  void cleanUp() {
    nonDefaultDbConnection.close();
    shutdown(nonDefaultDbClient);
    connection.close();
    shutdown(redisClient);
    redisServer.stop();
  }

  @SuppressWarnings("deprecation") // RedisURI constructor
  @Test
  @EnabledIfSystemProperty(
      named = "otel.instrumentation.lettuce.connection-telemetry.enabled",
      matches = "true")
  void testConnectUsingGetOnConnectionFuture() {
    RedisClient testConnectionClient = RedisClient.create(embeddedDbUri);
    testConnectionClient.setOptions(CLIENT_OPTIONS);

    ConnectionFuture<StatefulRedisConnection<String, String>> connectionFuture =
        testConnectionClient.connectAsync(
            StringCodec.UTF8, RedisURI.create("redis://" + host + ":" + port + "?timeout=3s"));
    StatefulRedisConnection<String, String> connection1 = connectionFuture.join();
    cleanup.deferCleanup(() -> shutdown(testConnectionClient));
    cleanup.deferCleanup(connection1);

    assertThat(connection1).isNotNull();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("CONNECT")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(maybeStablePeerService(), "test-peer-service"))));
  }

  @SuppressWarnings("deprecation") // RedisURI constructor
  @Test
  @EnabledIfSystemProperty(
      named = "otel.instrumentation.lettuce.connection-telemetry.enabled",
      matches = "true")
  void testConnectExceptionInsideTheConnectionFuture() {
    RedisClient testConnectionClient = RedisClient.create(dbUriNonExistent);
    testConnectionClient.setOptions(CLIENT_OPTIONS);

    Exception exception =
        catchException(
            () -> {
              ConnectionFuture<StatefulRedisConnection<String, String>> connectionFuture =
                  testConnectionClient.connectAsync(
                      StringCodec.UTF8,
                      RedisURI.create("redis://" + host + ":" + incorrectPort + "?timeout=3s"));
              connectionFuture.get();
            });

    assertThat(exception).isInstanceOf(ExecutionException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("CONNECT")
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, incorrectPort),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(maybeStablePeerService(), "test-peer-service"))
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            EXCEPTION_TYPE,
                                            "io.netty.channel.AbstractChannel.AnnotatedConnectException"),
                                        satisfies(
                                            EXCEPTION_MESSAGE,
                                            val ->
                                                val.matches(
                                                    expectedConnectionRefusedMessagePattern(
                                                        incorrectPort))),
                                        satisfies(EXCEPTION_STACKTRACE, val -> val.isNotNull())))));
  }

  @Test
  void testSetCommandUsingFutureGetWithTimeout()
      throws ExecutionException, InterruptedException, TimeoutException {
    RedisFuture<String> redisFuture = asyncCommands.set("TESTSETKEY", "TESTSETVAL");
    String res = redisFuture.get(3, SECONDS);

    assertThat(res).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "SET TESTSETKEY ?"),
                            equalTo(DB_OPERATION_NAME, "SET"))));
  }

  @Test
  void testSelectDoesNotChangeEstablishedDatabaseIndex()
      throws ExecutionException, InterruptedException, TimeoutException {
    try {
      connection.sync().select(1);
      asyncCommands.set("SELECT_TEST_KEY", "SELECT_TEST_VALUE").get(3, SECONDS);

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("SELECT " + host + ":" + port)
                          .hasKind(SpanKind.CLIENT)
                          .hasAttributesSatisfyingExactly(
                              equalTo(SERVER_ADDRESS, host),
                              equalTo(SERVER_PORT, port),
                              equalTo(NETWORK_PEER_ADDRESS, ip),
                              equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                              equalTo(DB_SYSTEM_NAME, REDIS),
                              equalTo(DB_NAMESPACE, "0"),
                              equalTo(DB_QUERY_TEXT, "SELECT 1"),
                              equalTo(DB_OPERATION_NAME, "SELECT"))),
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("SET " + host + ":" + port)
                          .hasKind(SpanKind.CLIENT)
                          .hasAttributesSatisfyingExactly(
                              equalTo(SERVER_ADDRESS, host),
                              equalTo(SERVER_PORT, port),
                              equalTo(NETWORK_PEER_ADDRESS, ip),
                              equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                              equalTo(DB_SYSTEM_NAME, REDIS),
                              equalTo(DB_NAMESPACE, "0"),
                              equalTo(DB_QUERY_TEXT, "SET SELECT_TEST_KEY ?"),
                              equalTo(DB_OPERATION_NAME, "SET"))));
    } finally {
      connection.sync().select(0);
      testing.waitForTraces(3);
      testing.clearData();
    }
  }

  @Test
  void testGetCommandChainedWithThenAccept()
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<String> future = new CompletableFuture<>();
    Consumer<String> consumer =
        res -> {
          testing.runWithSpan("callback", () -> assertThat(res).isEqualTo("TESTVAL"));
          future.complete(res);
        };

    testing.runWithSpan(
        "parent",
        () -> {
          RedisFuture<String> redisFuture = asyncCommands.get("TESTKEY");
          redisFuture.thenAccept(consumer);
        });

    assertThat(future.get(10, SECONDS)).isEqualTo("TESTVAL");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("GET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "GET TESTKEY"),
                            equalTo(DB_OPERATION_NAME, "GET")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  // to make sure instrumentation's chained completion stages won't interfere with user's, while
  // still recording spans
  @Test
  void testGetNonExistentKeyCommandWithHandleAsyncAndChainedWithThenApply()
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<String> future = new CompletableFuture<>();

    String successStr = "KEY MISSING";

    BiFunction<String, Throwable, String> firstStage =
        (res, error) -> {
          testing.runWithSpan(
              "callback1",
              () -> {
                assertThat(res).isNull();
                assertThat(error).isNull();
              });
          return (res == null ? successStr : res);
        };
    Function<String, Object> secondStage =
        input -> {
          testing.runWithSpan(
              "callback2",
              () -> {
                assertThat(input).isEqualTo(successStr);
                future.complete(successStr);
              });
          return null;
        };

    testing.runWithSpan(
        "parent",
        () -> {
          RedisFuture<String> redisFuture = asyncCommands.get("NON_EXISTENT_KEY");
          redisFuture.handle(firstStage).thenApply(secondStage);
        });

    assertThat(future.get(10, SECONDS)).isEqualTo(successStr);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("GET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "GET NON_EXISTENT_KEY"),
                            equalTo(DB_OPERATION_NAME, "GET")),
                span ->
                    span.hasName("callback1")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("callback2")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void testCommandWithNoArgumentsUsingBiconsumer()
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<String> future = new CompletableFuture<>();
    BiConsumer<String, Throwable> biConsumer =
        (keyRetrieved, error) ->
            testing.runWithSpan(
                "callback",
                () -> {
                  assertThat(keyRetrieved).isNotNull();
                  future.complete(keyRetrieved);
                });

    testing.runWithSpan(
        "parent",
        () -> {
          RedisFuture<String> redisFuture = asyncCommands.randomkey();
          redisFuture.whenCompleteAsync(biConsumer);
        });

    assertThat(future.get(10, SECONDS)).isNotNull();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("RANDOMKEY " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "RANDOMKEY"),
                            equalTo(DB_OPERATION_NAME, "RANDOMKEY")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void testHashSetAndThenNestApplyToHashGetall()
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<Map<String, String>> future = new CompletableFuture<>();

    RedisFuture<String> hmsetFuture = asyncCommands.hmset("TESTHM", testHashMap);
    hmsetFuture.thenApplyAsync(
        setResult -> {
          // Wait for 'hmset' trace to get written
          testing.waitForTraces(1);

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

    assertThat(future.get(10, SECONDS)).isEqualTo(testHashMap);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("HMSET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "HMSET TESTHM firstname ? lastname ? age ?"),
                            equalTo(DB_OPERATION_NAME, "HMSET"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("HGETALL " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "HGETALL TESTHM"),
                            equalTo(DB_OPERATION_NAME, "HGETALL"))));
  }

  @Test
  void testCommandCompletesExceptionally() {
    // turn off auto flush to complete the command exceptionally manually
    connection.setAutoFlushCommands(false);
    cleanup.deferCleanup(() -> connection.setAutoFlushCommands(true));

    RedisFuture<Long> redisFuture = asyncCommands.del("key1", "key2");
    boolean completedExceptionally =
        ((AsyncCommand<?, ?, ?>) redisFuture)
            .completeExceptionally(new IllegalStateException("TestException"));

    redisFuture.exceptionally(
        error -> {
          assertThat(error).isNotNull();
          assertThat(error).isInstanceOf(IllegalStateException.class);
          assertThat(error.getMessage()).isEqualTo("TestException");
          throw new RuntimeException(error);
        });

    connection.flushCommands();
    Throwable thrown = catchThrowable(redisFuture::get);

    await()
        .untilAsserted(
            () -> {
              assertThat(thrown).isInstanceOf(ExecutionException.class);
              assertThat(completedExceptionally).isTrue();
            });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DEL " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.error())
                        .hasException(new IllegalStateException("TestException"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "DEL key1 key2"),
                            equalTo(DB_OPERATION_NAME, "DEL"),
                            equalTo(ERROR_TYPE, "java.lang.IllegalStateException"))));
  }

  @Test
  void testCancelCommandBeforeItFinishes() {
    connection.setAutoFlushCommands(false);
    cleanup.deferCleanup(() -> connection.setAutoFlushCommands(true));

    RedisFuture<Long> redisFuture =
        testing.runWithSpan("parent", () -> asyncCommands.sadd("SKEY", "1", "2"));
    redisFuture.whenCompleteAsync(
        (res, error) ->
            testing.runWithSpan(
                "callback",
                () -> {
                  assertThat(error).isNotNull();
                  assertThat(error).isInstanceOf(CancellationException.class);
                }));

    boolean cancelSuccess = redisFuture.cancel(true);
    connection.flushCommands();

    await().untilAsserted(() -> assertThat(cancelSuccess).isTrue());
    testing.waitAndAssertTraces(
        trace ->
            // the command span and the user callback span both start asynchronously on
            // cancellation, so the two sibling spans can appear in any order
            trace.hasSpansSatisfyingExactlyInAnyOrder(
                span ->
                    span.hasName("parent")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasTotalAttributeCount(0),
                span ->
                    span.hasName("SADD " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "SADD SKEY ? ?"),
                            equalTo(DB_OPERATION_NAME, "SADD"),
                            equalTo(booleanKey("lettuce.command.cancelled"), experimental(true))),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @ParameterizedTest
  @MethodSource("deferredFlushScenarios")
  void deferredFlushCommand(BatchScenario scenario) throws Exception {
    connection.setAutoFlushCommands(false);
    cleanup.deferCleanup(() -> connection.setAutoFlushCommands(true));

    List<RedisFuture<?>> futures = new ArrayList<>();
    for (BatchCommand command : scenario.commands) {
      futures.add(command.run(asyncCommands));
    }
    connection.flushCommands();
    for (RedisFuture<?> future : futures) {
      Throwable thrown = catchThrowable(() -> future.get(10, SECONDS));
      if (thrown != null) {
        assertThat(thrown.getCause().getClass().getName()).isEqualTo(scenario.errorType);
      }
    }

    if (scenario.isEmpty()) {
      // Empty flush writes no Redis commands, so there is no database client request to report.
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
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, scenario.queryText),
                            equalTo(DB_OPERATION_NAME, scenario.operationName),
                            equalTo(DB_OPERATION_BATCH_SIZE, scenario.batchSize),
                            equalTo(ERROR_TYPE, scenario.errorType))));
  }

  @Test
  void testNonDefaultDatabaseIndex() throws Exception {
    nonDefaultDbCommands.set("NONDEFAULTKEY", "NONDEFAULTVAL").get(10, SECONDS);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, String.valueOf(NON_DEFAULT_DB_INDEX)),
                            equalTo(DB_QUERY_TEXT, "SET NONDEFAULTKEY ?"),
                            equalTo(DB_OPERATION_NAME, "SET"))));
  }

  @Test
  void testNonDefaultDatabaseIndexOnBatch() throws Exception {
    nonDefaultDbConnection.setAutoFlushCommands(false);
    cleanup.deferCleanup(() -> nonDefaultDbConnection.setAutoFlushCommands(true));

    List<RedisFuture<String>> futures =
        asList(
            nonDefaultDbCommands.set("NONDEFAULTBATCH1", "v1"),
            nonDefaultDbCommands.set("NONDEFAULTBATCH2", "v2"));
    nonDefaultDbConnection.flushCommands();
    for (RedisFuture<String> future : futures) {
      future.get(10, SECONDS);
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("PIPELINE SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, String.valueOf(NON_DEFAULT_DB_INDEX)),
                            equalTo(
                                DB_QUERY_TEXT, "SET NONDEFAULTBATCH1 ?; SET NONDEFAULTBATCH2 ?"),
                            equalTo(DB_OPERATION_NAME, "PIPELINE SET"),
                            equalTo(DB_OPERATION_BATCH_SIZE, 2L))));
  }

  @Test
  void testNonDefaultDatabaseIndexOnConnect() {
    assumeTrue(isLettuce5());

    RedisClient client =
        RedisClient.create("redis://" + host + ":" + port + "/" + NON_DEFAULT_DB_INDEX);
    client.setOptions(CLIENT_OPTIONS);
    cleanup.deferCleanup(() -> shutdown(client));
    cleanup.deferCleanup(client.connect());

    // lettuce sends SELECT while connecting to a non-default database. Depending on the lettuce
    // version that span either nests under CONNECT or starts a new trace, so assert the span itself
    // instead of the trace it lands in.
    await()
        .untilAsserted(
            () -> {
              List<SpanData> selectSpans = new ArrayList<>();
              for (SpanData span : testing.spans()) {
                if (span.getName().startsWith("SELECT")) {
                  selectSpans.add(span);
                }
              }
              assertThat(selectSpans).hasSize(1);
              assertThat(selectSpans.get(0))
                  .hasName("SELECT " + host + ":" + port)
                  .hasKind(SpanKind.CLIENT)
                  .hasAttributesSatisfyingExactly(
                      equalTo(SERVER_ADDRESS, host),
                      equalTo(SERVER_PORT, port),
                      equalTo(NETWORK_PEER_ADDRESS, ip),
                      equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                      equalTo(DB_SYSTEM_NAME, REDIS),
                      equalTo(DB_NAMESPACE, String.valueOf(NON_DEFAULT_DB_INDEX)),
                      equalTo(DB_QUERY_TEXT, "SELECT " + NON_DEFAULT_DB_INDEX),
                      equalTo(DB_OPERATION_NAME, "SELECT"));
            });
  }

  private static boolean isLettuce5() {
    String version = RedisClient.class.getPackage().getImplementationVersion();
    // Implementation-Version is absent from the Lettuce 5.0 and 5.1 artifacts.
    return version == null || version.startsWith("5.");
  }

  private static Stream<Arguments> deferredFlushScenarios() {
    return Stream.of(
        // Empty flush writes no Redis commands.
        argumentSet("empty", BatchScenario.builder().build()),
        argumentSet(
            "single",
            BatchScenario.builder()
                .addCommand(commands -> commands.set("batch1", "v1"))
                .operationName("SET")
                .queryText("SET batch1 ?")
                .build()),
        argumentSet(
            "twoSameOperation",
            BatchScenario.builder()
                .addCommand(commands -> commands.set("batch1", "v1"))
                .addCommand(commands -> commands.set("batch2", "v2"))
                .operationName("PIPELINE SET")
                .queryText("SET batch1 ?; SET batch2 ?")
                .batchSize(2)
                .build()),
        argumentSet(
            "twoDifferentOperations",
            BatchScenario.builder()
                .addCommand(commands -> commands.set("batch1", "v1"))
                .addCommand(commands -> commands.get("batch1"))
                .operationName("PIPELINE")
                .queryText("SET batch1 ?; GET batch1")
                .batchSize(2)
                .build()),
        argumentSet(
            "earlierFailure",
            BatchScenario.builder()
                .addCommand(commands -> commands.configSet("not-a-real-config", "1"))
                .addCommand(commands -> commands.set("batch-after-error", "v1"))
                .operationName("PIPELINE")
                .queryText("CONFIG SET not-a-real-config ?; SET batch-after-error ?")
                .batchSize(2)
                .errorType("io.lettuce.core.RedisCommandExecutionException")
                .build()));
  }

  private static class BatchScenario {
    private final List<BatchCommand> commands;
    private final String operationName;
    private final String queryText;
    private final Long batchSize;
    private final String errorType;

    private BatchScenario(Builder builder) {
      this.commands = builder.commands;
      this.operationName = builder.operationName;
      this.queryText = builder.queryText;
      this.batchSize = builder.batchSize;
      this.errorType = builder.errorType;
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
      private String errorType;

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

      private Builder errorType(String errorType) {
        this.errorType = errorType;
        return this;
      }

      private BatchScenario build() {
        return new BatchScenario(this);
      }
    }
  }

  private interface BatchCommand {
    RedisFuture<?> run(RedisAsyncCommands<String, String> commands);
  }

  @Test
  void testDebugSegfaultCommandWithNoArgumentShouldProduceSpan() {
    withIsolatedContainer(
        (connection, port) -> {
          RedisAsyncCommands<String, String> commands = connection.async();
          commands.debugSegfault();

          testing.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("DEBUG " + host + ":" + port)
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(SERVER_ADDRESS, host),
                                  equalTo(SERVER_PORT, port),
                                  equalTo(DB_SYSTEM_NAME, REDIS),
                                  equalTo(DB_NAMESPACE, "0"),
                                  equalTo(DB_QUERY_TEXT, "DEBUG SEGFAULT"),
                                  equalTo(DB_OPERATION_NAME, "DEBUG"))));
        });
  }

  @Test
  void testShutdownCommandShouldProduceSpan() {
    withIsolatedContainer(
        (connection, port) -> {
          RedisAsyncCommands<String, String> commands = connection.async();
          commands.shutdown(false);

          testing.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("SHUTDOWN " + host + ":" + port)
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(SERVER_ADDRESS, host),
                                  equalTo(SERVER_PORT, port),
                                  equalTo(DB_SYSTEM_NAME, REDIS),
                                  equalTo(DB_NAMESPACE, "0"),
                                  equalTo(DB_QUERY_TEXT, "SHUTDOWN NOSAVE"),
                                  equalTo(DB_OPERATION_NAME, "SHUTDOWN"))));
        });
  }
}
