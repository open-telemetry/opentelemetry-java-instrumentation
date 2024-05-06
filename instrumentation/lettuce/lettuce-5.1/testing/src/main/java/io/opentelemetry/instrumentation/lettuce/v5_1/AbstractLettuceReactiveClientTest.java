/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings({"InterruptedExceptionSwallowed"})
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
    getInstrumentationExtension().waitForTraces(1);
    getInstrumentationExtension().clearData();
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
            getInstrumentationExtension()
                .runWithSpan(
                    "callback",
                    () -> {
                      assertThat(res).isEqualTo("OK");
                      future.complete(res);
                    });

    getInstrumentationExtension()
        .runWithSpan(
            "parent", () -> reactiveCommands.set("TESTSETKEY", "TESTSETVAL").subscribe(consumer));

    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("OK");

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
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

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
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
  }

  // to make sure instrumentation's chained completion stages won't interfere with user's, while
  // still recording spans
  @Test
  void testGetNonExistentKeyCommand() throws Exception {
    CompletableFuture<String> future = new CompletableFuture<>();
    String defaultVal = "NOT THIS VALUE";

    getInstrumentationExtension()
        .runWithSpan(
            "parent",
            () -> {
              reactiveCommands
                  .get("NON_EXISTENT_KEY")
                  .defaultIfEmpty(defaultVal)
                  .subscribe(
                      res ->
                          getInstrumentationExtension()
                              .runWithSpan(
                                  "callback",
                                  () -> {
                                    assertThat(res).isEqualTo(defaultVal);
                                    future.complete(res);
                                  }));
            });

    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(defaultVal);

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
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
                                    DbIncubatingAttributes.DB_STATEMENT, "GET NON_EXISTENT_KEY"))
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
    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("RANDOMKEY")
                            .hasKind(SpanKind.CLIENT)
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
  }

  @Test
  void testCommandFluxPublisher() {
    reactiveCommands.command().subscribe();

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("COMMAND")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "COMMAND"))
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
    assertThat(getInstrumentationExtension().spans().size()).isEqualTo(0);
  }

  @Test
  void testBlockingSubscriber() {
    getInstrumentationExtension()
        .runWithSpan(
            "test-parent",
            () -> reactiveCommands.set("a", "1").then(reactiveCommands.get("a")).block());

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("test-parent").hasAttributes(Attributes.empty()),
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "SET a ?"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end")),
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
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "GET a"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  @Test
  void testAsyncSubscriber() {
    getInstrumentationExtension()
        .runWithSpan(
            "test-parent",
            () -> reactiveCommands.set("a", "1").then(reactiveCommands.get("a")).subscribe());

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("test-parent").hasAttributes(Attributes.empty()),
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "SET a ?"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end")),
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
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "GET a"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }
}
