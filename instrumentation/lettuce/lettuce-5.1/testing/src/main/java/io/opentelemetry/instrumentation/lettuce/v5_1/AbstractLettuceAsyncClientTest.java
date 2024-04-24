/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
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
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"InterruptedExceptionSwallowed"})
public abstract class AbstractLettuceAsyncClientTest extends AbstractLettuceClientTest {
  private static String dbUriNonExistent;
  private static int incorrectPort;

  private static final ImmutableMap<String, String> testHashMap =
      ImmutableMap.of(
          "firstname", "John",
          "lastname", "Doe",
          "age", "53");

  private static RedisAsyncCommands<String, String> asyncCommands;

  @BeforeAll
  void setUp() throws UnknownHostException {
    redisServer.start();
    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    incorrectPort = PortUtils.findOpenPort();
    dbUriNonExistent = "redis://" + host + ":" + incorrectPort + "/" + DB_INDEX;

    redisClient = createClient(embeddedDbUri);
    redisClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS);

    connection = redisClient.connect();
    asyncCommands = connection.async();
    RedisCommands<String, String> syncCommands = connection.sync();

    syncCommands.set("TESTKEY", "TESTVAL");

    // 1 set trace
    getInstrumentationExtension().waitForTraces(1);
    getInstrumentationExtension().clearData();
  }

  @AfterAll
  static void cleanUp() {
    connection.close();
    redisClient.shutdown();
    redisServer.stop();
  }

  boolean testCallback() {
    return true;
  }

  @Test
  void testConnectUsingGetOnConnectionFuture() throws Exception {
    RedisClient testConnectionClient = RedisClient.create(embeddedDbUri);
    testConnectionClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS);

    ConnectionFuture<StatefulRedisConnection<String, String>> connectionFuture =
        testConnectionClient.connectAsync(
            StringCodec.UTF8, RedisURI.create("redis://" + host + ":" + port + "?timeout=3s"));
    StatefulRedisConnection<String, String> connection1 = connectionFuture.get();
    cleanup.deferCleanup(connection1);
    cleanup.deferCleanup(testConnectionClient::shutdown);

    assertThat(connection1).isNotNull();
    // Lettuce tracing does not trace connect
    assertThat(getInstrumentationExtension().spans()).isEmpty();
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
    assertThat(getInstrumentationExtension().spans()).isEmpty();
  }

  @Test
  void testSetCommandUsingFutureGetWithTimeout() throws Exception {
    RedisFuture<String> redisFuture = asyncCommands.set("TESTSETKEY", "TESTSETVAL");
    String res = redisFuture.get(3, TimeUnit.SECONDS);

    assertThat(res).isEqualTo("OK");

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "SET TESTSETKEY ?"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  @Test
  void testGetCommandChainedWithThenAccept() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    Consumer<String> consumer =
        res -> {
          if (testCallback()) {
            getInstrumentationExtension()
                .runWithSpan("callback", () -> assertThat(res).isEqualTo("TESTVAL"));
          }
          future.complete(res);
        };

    getInstrumentationExtension()
        .runWithSpan(
            "parent",
            () -> {
              RedisFuture<String> redisFuture = asyncCommands.get("TESTKEY");
              redisFuture.thenAccept(consumer);
            });

    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("TESTVAL");

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> spanAsserts =
                  new ArrayList<>(
                      Arrays.asList(
                          span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                          span ->
                              span.hasName("GET")
                                  .hasKind(SpanKind.CLIENT)
                                  .hasParent(trace.getSpan(0))
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                      equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                      equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                      equalTo(ServerAttributes.SERVER_PORT, port),
                                      equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                      equalTo(DbIncubatingAttributes.DB_STATEMENT, "GET TESTKEY"))
                                  .hasEventsSatisfyingExactly(
                                      event -> event.hasName("redis.encode.start"),
                                      event -> event.hasName("redis.encode.end"))));

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
            getInstrumentationExtension()
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
            getInstrumentationExtension()
                .runWithSpan(
                    "callback2",
                    () -> {
                      assertThat(input).isEqualTo(successStr);
                    });
          }
          future.complete(successStr);
          return null;
        };

    getInstrumentationExtension()
        .runWithSpan(
            "parent",
            () -> {
              RedisFuture<String> redisFuture = asyncCommands.get("NON_EXISTENT_KEY");
              redisFuture.handleAsync(firstStage).thenApply(secondStage);
            });

    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(successStr);

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> spanAsserts =
                  new ArrayList<>(
                      Arrays.asList(
                          span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                          span ->
                              span.hasName("GET")
                                  .hasKind(SpanKind.CLIENT)
                                  .hasParent(trace.getSpan(0))
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                      equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                      equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                      equalTo(ServerAttributes.SERVER_PORT, port),
                                      equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                      equalTo(
                                          DbIncubatingAttributes.DB_STATEMENT,
                                          "GET NON_EXISTENT_KEY"))
                                  .hasEventsSatisfyingExactly(
                                      event -> event.hasName("redis.encode.start"),
                                      event -> event.hasName("redis.encode.end"))));

              if (testCallback()) {
                spanAsserts.addAll(
                    Arrays.asList(
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
            getInstrumentationExtension()
                .runWithSpan(
                    "callback",
                    () -> {
                      assertThat(keyRetrieved).isNotNull();
                    });
          }
          future.complete(keyRetrieved);
        };

    getInstrumentationExtension()
        .runWithSpan(
            "parent",
            () -> {
              RedisFuture<String> redisFuture = asyncCommands.randomkey();
              redisFuture.whenCompleteAsync(biConsumer);
            });

    assertThat(future.get(10, TimeUnit.SECONDS)).isNotNull();

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace -> {
              List<Consumer<SpanDataAssert>> spanAsserts =
                  new ArrayList<>(
                      Arrays.asList(
                          span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                          span ->
                              span.hasName("RANDOMKEY")
                                  .hasKind(SpanKind.CLIENT)
                                  .hasParent(trace.getSpan(0))
                                  .hasAttributesSatisfyingExactly(
                                      equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                      equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                      equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                      equalTo(ServerAttributes.SERVER_PORT, port),
                                      equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                      equalTo(DbIncubatingAttributes.DB_STATEMENT, "RANDOMKEY"))
                                  .hasEventsSatisfyingExactly(
                                      event -> event.hasName("redis.encode.start"),
                                      event -> event.hasName("redis.encode.end"))));

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

    RedisFuture<String> hmsetFuture = asyncCommands.hmset("TESTHM", testHashMap);
    hmsetFuture.thenApplyAsync(
        setResult -> {
          // Wait for 'hmset' trace to get written
          getInstrumentationExtension().waitForTraces(1);

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

    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(testHashMap);

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("HMSET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(
                                    DbIncubatingAttributes.DB_STATEMENT,
                                    "HMSET TESTHM firstname ? lastname ? age ?"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))),
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("HGETALL")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "HGETALL TESTHM"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }
}
