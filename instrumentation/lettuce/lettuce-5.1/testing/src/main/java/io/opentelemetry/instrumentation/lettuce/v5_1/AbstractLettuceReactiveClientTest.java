/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"InterruptedExceptionSwallowed", "deprecation"}) // using deprecated semconv
public abstract class AbstractLettuceReactiveClientTest extends AbstractLettuceClientTest {

  protected static String expectedHostAttributeValue;

  protected static RedisReactiveCommands<String, String> reactiveCommands;

  @BeforeAll
  void setUp() throws UnknownHostException {
    redisServer.start();

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;
    expectedHostAttributeValue = Objects.equals(host, "127.0.0.1") ? null : host;

    redisClient = createClient(embeddedDbUri);
    redisClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS);

    connection = redisClient.connect();
    reactiveCommands = connection.reactive();
    RedisCommands<String, String> syncCommands = connection.sync();

    syncCommands.set("TESTKEY", "TESTVAL");

    // 1 set trace
    testing().waitForTraces(1);
    testing().clearData();
  }

  @AfterAll
  static void cleanUp() {
    connection.close();
    redisClient.shutdown();
    redisServer.stop();
  }

  @Test
  void testSetCommandWithSubscribeOnDefinedConsumer() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();

    Consumer<String> consumer =
        res ->
            testing()
                .runWithSpan(
                    "callback",
                    () -> {
                      assertThat(res).isEqualTo("OK");
                      future.complete(res);
                    });

    testing()
        .runWithSpan(
            "parent", () -> reactiveCommands.set("TESTSETKEY", "TESTSETVAL").subscribe(consumer));

    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("OK");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, "ipv4"),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), "redis"),
                                    equalTo(maybeStable(DB_STATEMENT), "SET TESTSETKEY ?")))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end")),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  void testGetCommandWithLambdaFunction() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();

    reactiveCommands
        .get("TESTKEY")
        .subscribe(
            res -> {
              assertThat(res).isEqualTo("TESTVAL");
              future.complete(res);
            });

    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("TESTVAL");

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, "ipv4"),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), "redis"),
                                    equalTo(maybeStable(DB_STATEMENT), "GET TESTKEY")))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  // to make sure instrumentation's chained completion stages won't interfere with user's, while
  // still recording spans
  @Test
  void testGetNonExistentKeyCommand() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    String defaultVal = "NOT THIS VALUE";

    testing()
        .runWithSpan(
            "parent",
            () -> {
              reactiveCommands
                  .get("NON_EXISTENT_KEY")
                  .defaultIfEmpty(defaultVal)
                  .subscribe(
                      res ->
                          testing()
                              .runWithSpan(
                                  "callback",
                                  () -> {
                                    assertThat(res).isEqualTo(defaultVal);
                                    future.complete(res);
                                  }));
            });

    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(defaultVal);

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, "ipv4"),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), "redis"),
                                    equalTo(maybeStable(DB_STATEMENT), "GET NON_EXISTENT_KEY")))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end")),
                    span ->
                        span.hasName("callback")
                            .hasKind(SpanKind.INTERNAL)
                            .hasParent(trace.getSpan(0))));
  }

  @Test
  void testCommandWithNoArguments() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();

    reactiveCommands
        .randomkey()
        .subscribe(
            res -> {
              assertThat(res).isEqualTo("TESTKEY");
              future.complete(res);
            });

    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("TESTKEY");
    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("RANDOMKEY")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, "ipv4"),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), "redis"),
                                    equalTo(maybeStable(DB_STATEMENT), "RANDOMKEY")))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  @Test
  void testCommandFluxPublisher() {
    reactiveCommands.command().subscribe();

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("COMMAND")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, "ipv4"),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), "redis"),
                                    equalTo(maybeStable(DB_STATEMENT), "COMMAND")))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  @Test
  void testNonReactiveCommandShouldNotProduceSpan() throws Exception {
    Class<?> commandsClass = RedisReactiveCommands.class;
    java.lang.reflect.Method digestMethod;
    // The digest() signature changed between 5 -> 6
    try {
      digestMethod = commandsClass.getMethod("digest", String.class);
    } catch (NoSuchMethodException unused) {
      digestMethod = commandsClass.getMethod("digest", Object.class);
    }
    String res = (String) digestMethod.invoke(reactiveCommands, "test");

    assertThat(res).isNotNull();
    assertThat(testing().spans().size()).isEqualTo(0);
  }

  @Test
  void testBlockingSubscriber() {
    testing()
        .runWithSpan(
            "test-parent",
            () -> reactiveCommands.set("a", "1").then(reactiveCommands.get("a")).block());

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("test-parent").hasAttributes(Attributes.empty()),
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, "ipv4"),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), "redis"),
                                    equalTo(maybeStable(DB_STATEMENT), "SET a ?")))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end")),
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, "ipv4"),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), "redis"),
                                    equalTo(maybeStable(DB_STATEMENT), "GET a")))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  @Test
  void testAsyncSubscriber() {
    testing()
        .runWithSpan(
            "test-parent",
            () -> reactiveCommands.set("a", "1").then(reactiveCommands.get("a")).subscribe());

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("test-parent").hasAttributes(Attributes.empty()),
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, "ipv4"),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), "redis"),
                                    equalTo(maybeStable(DB_STATEMENT), "SET a ?")))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end")),
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, "ipv4"),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), "redis"),
                                    equalTo(maybeStable(DB_STATEMENT), "GET a")))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }
}
