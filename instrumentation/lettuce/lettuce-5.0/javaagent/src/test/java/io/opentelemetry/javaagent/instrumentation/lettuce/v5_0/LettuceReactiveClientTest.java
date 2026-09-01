/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.ExperimentalHelper.EXPERIMENTAL_ATTRIBUTES_ENABLED;
import static io.opentelemetry.javaagent.instrumentation.lettuce.v5_0.ExperimentalHelper.experimental;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@SuppressWarnings("deprecation") // using deprecated semconv
class LettuceReactiveClientTest extends AbstractLettuceClientTest {
  private RedisReactiveCommands<String, String> reactiveCommands;

  @BeforeAll
  void setUp() throws UnknownHostException {
    redisServer.start();

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    redisClient = RedisClient.create(embeddedDbUri);
    redisClient.setOptions(CLIENT_OPTIONS);

    connection = redisClient.connect();
    reactiveCommands = connection.reactive();
    RedisCommands<String, String> syncCommands = connection.sync();

    syncCommands.set("TESTKEY", "TESTVAL");

    testing.waitForTraces(connectionTelemetryEnabled() ? 2 : 1);
  }

  @AfterAll
  void cleanUp() {
    connection.close();
    shutdown(redisClient);
    redisServer.stop();
  }

  @Test
  void testSetCommandWithSubscribeOnDefinedConsumer()
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<String> future = new CompletableFuture<>();

    Consumer<String> consumer =
        res ->
            testing.runWithSpan(
                "callback",
                () -> {
                  assertThat(res).isEqualTo("OK");
                  future.complete(res);
                });

    testing.runWithSpan(
        "parent", () -> reactiveCommands.set("TESTSETKEY", "TESTSETVAL").subscribe(consumer));

    assertThat(future.get(10, SECONDS)).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? Long.valueOf(port) : null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "SET TESTSETKEY ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void testGetCommandWithLambdaFunction()
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<String> future = new CompletableFuture<>();

    reactiveCommands
        .get("TESTKEY")
        .subscribe(
            res -> {
              assertThat(res).isEqualTo("TESTVAL");
              future.complete(res);
            });

    assertThat(future.get(10, SECONDS)).isEqualTo("TESTVAL");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? Long.valueOf(port) : null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "GET TESTKEY"),
                            equalTo(maybeStable(DB_OPERATION), "GET"))));
  }

  // to make sure instrumentation's chained completion stages won't interfere with user's, while
  // still recording spans
  @Test
  void testGetNonExistentKeyCommand()
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<String> future = new CompletableFuture<>();
    String defaultVal = "NOT THIS VALUE";

    testing.runWithSpan(
        "parent",
        () -> {
          reactiveCommands
              .get("NON_EXISTENT_KEY")
              .defaultIfEmpty(defaultVal)
              .subscribe(
                  res ->
                      testing.runWithSpan(
                          "callback",
                          () -> {
                            assertThat(res).isEqualTo(defaultVal);
                            future.complete(res);
                          }));
        });

    assertThat(future.get(10, SECONDS)).isEqualTo(defaultVal);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? Long.valueOf(port) : null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "GET NON_EXISTENT_KEY"),
                            equalTo(maybeStable(DB_OPERATION), "GET")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void testCommandWithNoArguments()
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<String> future = new CompletableFuture<>();

    reactiveCommands
        .randomkey()
        .subscribe(
            res -> {
              assertThat(res).isEqualTo("TESTKEY");
              future.complete(res);
            });

    assertThat(future.get(10, SECONDS)).isEqualTo("TESTKEY");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "RANDOMKEY " + host + ":" + port
                                : "RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? Long.valueOf(port) : null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "RANDOMKEY"),
                            equalTo(maybeStable(DB_OPERATION), "RANDOMKEY"))));
  }

  @Test
  void testCommandFluxPublisher() {
    reactiveCommands.command().subscribe();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "COMMAND " + host + ":" + port
                                : "COMMAND")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? Long.valueOf(port) : null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "COMMAND"),
                            equalTo(maybeStable(DB_OPERATION), "COMMAND"),
                            satisfies(
                                longKey("lettuce.command.results.count"),
                                val -> {
                                  if (EXPERIMENTAL_ATTRIBUTES_ENABLED) {
                                    val.isGreaterThan(100);
                                  }
                                }))));
  }

  @Test
  void testCommandCancelAfter2OnFluxPublisher() {
    reactiveCommands.command().take(2).subscribe();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "COMMAND " + host + ":" + port
                                : "COMMAND")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? Long.valueOf(port) : null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "COMMAND"),
                            equalTo(maybeStable(DB_OPERATION), "COMMAND"),
                            equalTo(booleanKey("lettuce.command.cancelled"), experimental(true)),
                            satisfies(
                                longKey("lettuce.command.results.count"),
                                val -> {
                                  if (EXPERIMENTAL_ATTRIBUTES_ENABLED) {
                                    val.isEqualTo(2);
                                  }
                                }))));
  }

  @Test
  void testNonReactiveCommandShouldNotProduceSpan() {
    // digest computes a SHA locally without contacting redis
    String res = reactiveCommands.digest("test");

    assertThat(res).isNotNull();
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void testDebugSegfaultCommandReturnsMonoVoidWithNoArgumentShouldProduceSpan() {
    withIsolatedContainer(
        (connection, port) -> {
          RedisReactiveCommands<String, String> commands = connection.reactive();
          commands.debugSegfault().subscribe();

          testing.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(
                                  emitStableDatabaseSemconv()
                                      ? "DEBUG " + host + ":" + port
                                      : "DEBUG")
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(SERVER_ADDRESS, host),
                                  equalTo(SERVER_PORT, port),
                                  equalTo(NETWORK_PEER_ADDRESS, null),
                                  equalTo(NETWORK_PEER_PORT, null),
                                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                                  equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                                  equalTo(maybeStable(DB_STATEMENT), "DEBUG SEGFAULT"),
                                  equalTo(maybeStable(DB_OPERATION), "DEBUG"))));
        });
  }

  @Test
  void testShutdownCommandShouldProduceSpan() {
    withIsolatedContainer(
        (connection, port) -> {
          RedisReactiveCommands<String, String> commands = connection.reactive();
          commands.shutdown(false).subscribe();

          testing.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(
                                  emitStableDatabaseSemconv()
                                      ? "SHUTDOWN " + host + ":" + port
                                      : "SHUTDOWN")
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(SERVER_ADDRESS, host),
                                  equalTo(SERVER_PORT, port),
                                  equalTo(NETWORK_PEER_ADDRESS, null),
                                  equalTo(NETWORK_PEER_PORT, null),
                                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                                  equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                                  equalTo(maybeStable(DB_STATEMENT), "SHUTDOWN NOSAVE"),
                                  equalTo(maybeStable(DB_OPERATION), "SHUTDOWN"))));
        });
  }

  @Test
  void testBlockingSubscriber() {
    testing.runWithSpan(
        "test-parent",
        () -> reactiveCommands.set("a", "1").then(reactiveCommands.get("a")).block());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test-parent").hasTotalAttributeCount(0),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? Long.valueOf(port) : null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "SET a ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET")),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? Long.valueOf(port) : null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "GET a"),
                            equalTo(maybeStable(DB_OPERATION), "GET"))));
  }

  @Test
  void resubscribedCommandRetainsPeerCapture() {
    Mono<String> command = reactiveCommands.set("resubscribed", "value");
    assertThat(command.block()).isEqualTo("OK");
    assertThat(command.block()).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(this::assertResubscribedSetSpan),
        trace -> trace.hasSpansSatisfyingExactly(this::assertResubscribedSetSpan));
  }

  @Test
  void overlappingSubscriptionsUseIndependentCommands() throws Exception {
    redisServer.execInContainer("redis-cli", "DEL", "overlapping");
    Mono<KeyValue<String, String>> command = reactiveCommands.blpop(30, "overlapping");

    CompletableFuture<KeyValue<String, String>> first = command.toFuture();
    CompletableFuture<KeyValue<String, String>> second = command.toFuture();

    await()
        .atMost(Duration.ofSeconds(10))
        .until(
            () ->
                Arrays.stream(
                            redisServer
                                .execInContainer("redis-cli", "CLIENT", "LIST")
                                .getStdout()
                                .split("\\R"))
                        .filter(line -> line.contains("cmd=blpop"))
                        .count()
                    == 1);
    assertThat(first).isNotDone();
    assertThat(second).isNotDone();

    redisServer.execInContainer("redis-cli", "LPUSH", "overlapping", "first", "second");

    assertThat(first.get(10, SECONDS).getKey()).isEqualTo("overlapping");
    assertThat(second.get(10, SECONDS).getKey()).isEqualTo("overlapping");

    testing.waitAndAssertTraces(
        trace -> trace.hasSpansSatisfyingExactly(this::assertOverlappingBlpopSpan),
        trace -> trace.hasSpansSatisfyingExactly(this::assertOverlappingBlpopSpan));
  }

  private void assertOverlappingBlpopSpan(SpanDataAssert span) {
    span.hasName(emitStableDatabaseSemconv() ? "BLPOP " + host + ":" + port : "BLPOP")
        .hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfyingExactly(
            equalTo(SERVER_ADDRESS, host),
            equalTo(SERVER_PORT, port),
            equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
            equalTo(NETWORK_PEER_PORT, emitStableDatabaseSemconv() ? Long.valueOf(port) : null),
            equalTo(maybeStable(DB_SYSTEM), REDIS),
            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
            equalTo(maybeStable(DB_STATEMENT), "BLPOP overlapping 30"),
            equalTo(maybeStable(DB_OPERATION), "BLPOP"));
  }

  private void assertResubscribedSetSpan(SpanDataAssert span) {
    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
        .hasKind(SpanKind.CLIENT)
        .hasAttributesSatisfyingExactly(
            equalTo(SERVER_ADDRESS, host),
            equalTo(SERVER_PORT, port),
            equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
            equalTo(NETWORK_PEER_PORT, emitStableDatabaseSemconv() ? Long.valueOf(port) : null),
            equalTo(maybeStable(DB_SYSTEM), REDIS),
            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
            equalTo(maybeStable(DB_STATEMENT), "SET resubscribed ?"),
            equalTo(maybeStable(DB_OPERATION), "SET"));
  }

  @Test
  void testAsyncSubscriber() {
    testing.runWithSpan(
        "test-parent",
        () -> reactiveCommands.set("a", "1").then(reactiveCommands.get("a")).subscribe());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test-parent").hasTotalAttributeCount(0),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? Long.valueOf(port) : null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "SET a ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET")),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? Long.valueOf(port) : null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "GET a"),
                            equalTo(maybeStable(DB_OPERATION), "GET"))));
  }

  @Test
  void testAsyncSubscriberWithSpecificThreadPool() {
    testing.runWithSpan(
        "test-parent",
        () ->
            reactiveCommands
                .set("a", "1")
                .then(reactiveCommands.get("a"))
                .subscribeOn(Schedulers.parallel())
                .subscribe());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test-parent").hasTotalAttributeCount(0),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? Long.valueOf(port) : null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "SET a ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET")),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, emitStableDatabaseSemconv() ? ip : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? Long.valueOf(port) : null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "GET a"),
                            equalTo(maybeStable(DB_OPERATION), "GET"))));
  }
}
