/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_RESPONSE_STATUS_CODE;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.awaitility.Awaitility.await;

import io.lettuce.core.ConnectionFuture;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandTimeoutException;
import io.lettuce.core.RedisException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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

@SuppressWarnings({"InterruptedExceptionSwallowed", "deprecation"}) // using deprecated semconv
public abstract class AbstractLettuceAsyncClientTest extends AbstractLettuceClientTest {
  private String dbUriNonExistent;
  private int incorrectPort;

  private static final Map<String, String> TEST_HASH_MAP;

  static {
    Map<String, String> map = new LinkedHashMap<>();
    map.put("firstname", "John");
    map.put("lastname", "Doe");
    map.put("age", "53");
    TEST_HASH_MAP = Collections.unmodifiableMap(map);
  }

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
  }

  boolean testCallback() {
    return true;
  }

  protected boolean connectHasSpans() {
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
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "SET TESTSETKEY ?"),
                                    equalTo(DB_OPERATION_NAME, "SET")))));

    assertCommandMetric("SET", null);
  }

  @Test
  void testLpushWrongTypeCommandSetsSpanStatus() throws Exception {
    asyncCommands.set(WRONG_TYPE_KEY, WRONG_TYPE_VALUE).get(3, SECONDS);
    testing().waitForTraces(1);
    testing().clearData();

    RedisFuture<Long> redisFuture = asyncCommands.lpush(WRONG_TYPE_KEY, WRONG_TYPE_VALUE);

    Throwable thrown = catchThrowable(() -> redisFuture.get(3, SECONDS));

    assertThat(thrown)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(RedisCommandExecutionException.class);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("LPUSH"))
                            .hasKind(SpanKind.CLIENT)
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "LPUSH " + WRONG_TYPE_KEY + " ?"),
                                    equalTo(DB_OPERATION_NAME, "LPUSH"),
                                    equalTo(
                                        ERROR_TYPE,
                                        testLatestDeps()
                                            ? RedisCommandExecutionException.class.getName()
                                            : "_OTHER"),
                                    equalTo(
                                        DB_RESPONSE_STATUS_CODE,
                                        testLatestDeps() ? "WRONGTYPE" : null)))
                            .satisfies(AbstractLettuceClientTest::assertCommandErrorEvents)));

    assertCommandMetric(
        "LPUSH", testLatestDeps() ? RedisCommandExecutionException.class.getName() : "_OTHER");
  }

  @Test
  void testCommandTimeout() {
    RedisClient timeoutClient = createClient(embeddedDbUri);
    cleanup.deferCleanup(timeoutClient::shutdown);
    timeoutClient.setOptions(
        LettuceTestUtil.CLIENT_OPTIONS
            .mutate()
            .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(100)))
            .build());
    StatefulRedisConnection<String, String> timeoutConnection = timeoutClient.connect();
    cleanup.deferCleanup(timeoutConnection);
    if (testLatestDeps()) {
      // ignore CLIENT SETINFO and MAINT_NOTIFICATIONS traces
      testing().waitForTraces(3);
      testing().clearData();
    }

    RedisFuture<?> redisFuture = timeoutConnection.async().blpop(1, "TIMEOUT_LIST");
    Throwable thrown = catchThrowable(() -> redisFuture.get(3, SECONDS));

    assertThat(thrown)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(RedisCommandTimeoutException.class);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("BLPOP"))
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "BLPOP TIMEOUT_LIST 1"),
                                    equalTo(DB_OPERATION_NAME, "BLPOP"),
                                    equalTo(
                                        ERROR_TYPE,
                                        testLatestDeps()
                                            ? RedisCommandTimeoutException.class.getName()
                                            : "_OTHER")))));

    assertCommandMetric(
        "BLPOP", testLatestDeps() ? RedisCommandTimeoutException.class.getName() : "_OTHER");
  }

  @Test
  void testPendingCommandDisconnect() {
    StatefulRedisConnection<String, String> pendingConnection = redisClient.connect();
    cleanup.deferCleanup(pendingConnection);
    pendingConnection.sync().clientSetname("otel-pending-disconnect");
    testing().waitForTraces(testLatestDeps() ? 4 : 1);
    testing().clearData();

    RedisClient observerClient = RedisClient.create(embeddedDbUri);
    cleanup.deferCleanup(observerClient::shutdown);
    StatefulRedisConnection<String, String> observer = observerClient.connect();
    cleanup.deferCleanup(observer);

    RedisFuture<?> pending = pendingConnection.async().blpop(0, "PENDING_DISCONNECT_LIST");
    await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                Stream.of(observer.sync().clientList().split("\n"))
                    .anyMatch(
                        line ->
                            line.contains("name=otel-pending-disconnect")
                                && line.contains("cmd=blpop")));
    assertThat(pending.isDone()).isFalse();

    pendingConnection.close();
    Throwable thrown = catchThrowable(() -> pending.get(3, SECONDS));
    assertThat(thrown)
        .isInstanceOf(ExecutionException.class)
        .hasCauseInstanceOf(RedisException.class);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("BLPOP"))
                            .hasKind(SpanKind.CLIENT)
                            .hasNoParent()
                            .hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "BLPOP PENDING_DISCONNECT_LIST 0"),
                                    equalTo(DB_OPERATION_NAME, "BLPOP"),
                                    equalTo(
                                        ERROR_TYPE,
                                        testLatestDeps()
                                            ? RedisException.class.getName()
                                            : "_OTHER")))));

    assertCommandMetric("BLPOP", testLatestDeps() ? RedisException.class.getName() : "_OTHER");
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
                                          equalTo(NETWORK_PEER_ADDRESS, ip),
                                          equalTo(NETWORK_PEER_PORT, port),
                                          equalTo(SERVER_ADDRESS, host),
                                          equalTo(SERVER_PORT, port),
                                          equalTo(DB_SYSTEM_NAME, REDIS),
                                          equalTo(DB_QUERY_TEXT, "GET TESTKEY"),
                                          equalTo(DB_OPERATION_NAME, "GET")))));

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
                                          equalTo(NETWORK_PEER_ADDRESS, ip),
                                          equalTo(NETWORK_PEER_PORT, port),
                                          equalTo(SERVER_ADDRESS, host),
                                          equalTo(SERVER_PORT, port),
                                          equalTo(DB_SYSTEM_NAME, REDIS),
                                          equalTo(DB_QUERY_TEXT, "GET NON_EXISTENT_KEY"),
                                          equalTo(DB_OPERATION_NAME, "GET")))));

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
                                          equalTo(NETWORK_PEER_ADDRESS, ip),
                                          equalTo(NETWORK_PEER_PORT, port),
                                          equalTo(SERVER_ADDRESS, host),
                                          equalTo(SERVER_PORT, port),
                                          equalTo(DB_SYSTEM_NAME, REDIS),
                                          equalTo(DB_QUERY_TEXT, "RANDOMKEY"),
                                          equalTo(DB_OPERATION_NAME, "RANDOMKEY")))));

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
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(
                                        DB_QUERY_TEXT, "HMSET TESTHM firstname ? lastname ? age ?"),
                                    equalTo(DB_OPERATION_NAME, "HMSET")))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName(spanName("HGETALL"))
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(DB_SYSTEM_NAME, REDIS),
                                    equalTo(DB_QUERY_TEXT, "HGETALL TESTHM"),
                                    equalTo(DB_OPERATION_NAME, "HGETALL")))));
  }
}
