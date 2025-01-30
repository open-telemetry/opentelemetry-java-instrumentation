/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.reactive.RedisReactiveCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import org.assertj.core.api.AbstractBooleanAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.scheduler.Schedulers;

@SuppressWarnings("deprecation") // using deprecated semconv
class LettuceReactiveClientTest extends AbstractLettuceClientTest {
  private static RedisReactiveCommands<String, String> reactiveCommands;

  @BeforeAll
  static void setUp() throws UnknownHostException {
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

    // 1 set + 1 connect trace
    testing.waitForTraces(2);
    testing.clearData();
  }

  @AfterAll
  static void cleanUp() {
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

    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
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

    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("TESTVAL");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
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

    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo(defaultVal);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
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

    assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("TESTKEY");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
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
                    span.hasName("COMMAND")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "COMMAND"),
                            equalTo(maybeStable(DB_OPERATION), "COMMAND"),
                            satisfies(
                                AttributeKey.longKey("lettuce.command.results.count"),
                                val -> val.isGreaterThan(100)))));
  }

  @Test
  void testCommandCancelAfter2OnFluxPublisher() {
    reactiveCommands.command().take(2).subscribe();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("COMMAND")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "COMMAND"),
                            equalTo(maybeStable(DB_OPERATION), "COMMAND"),
                            satisfies(
                                AttributeKey.booleanKey("lettuce.command.cancelled"),
                                AbstractBooleanAssert::isTrue),
                            satisfies(
                                AttributeKey.longKey("lettuce.command.results.count"),
                                val -> val.isEqualTo(2)))));
  }

  @Test
  void testNonReactiveCommandShouldNotProduceSpan() {
    String res = reactiveCommands.digest(null);

    assertThat(res).isNotNull();
    assertThat(testing.spans().size()).isEqualTo(0);
  }

  @Test
  void testDebugSegfaultCommandReturnsMonoVoidWithNoArgumentShouldProduceSpan() {
    // Test Causes redis to crash therefore it needs its own container
    try (StatefulRedisConnection<String, String> statefulConnection = newContainerConnection()) {
      RedisReactiveCommands<String, String> commands = statefulConnection.reactive();
      commands.debugSegfault().subscribe();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DEBUG")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "DEBUG SEGFAULT"),
                            equalTo(maybeStable(DB_OPERATION), "DEBUG"))));
  }

  @Test
  void testShutdownCommandShouldProduceSpan() {
    // Test Causes redis to crash therefore it needs its own container
    try (StatefulRedisConnection<String, String> statefulConnection = newContainerConnection()) {
      RedisReactiveCommands<String, String> commands = statefulConnection.reactive();
      commands.shutdown(false).subscribe();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SHUTDOWN")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "SHUTDOWN NOSAVE"),
                            equalTo(maybeStable(DB_OPERATION), "SHUTDOWN"))));
  }

  @Test
  void testBlockingSubscriber() {
    testing.runWithSpan(
        "test-parent",
        () -> reactiveCommands.set("a", "1").then(reactiveCommands.get("a")).block());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test-parent").hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "SET a ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "GET a"),
                            equalTo(maybeStable(DB_OPERATION), "GET"))));
  }

  @Test
  void testAsyncSubscriber() {
    testing.runWithSpan(
        "test-parent",
        () -> reactiveCommands.set("a", "1").then(reactiveCommands.get("a")).subscribe());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test-parent").hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "SET a ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
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
                .subscribeOn(Schedulers.elastic())
                .subscribe());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test-parent").hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "SET a ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "GET a"),
                            equalTo(maybeStable(DB_OPERATION), "GET"))));
  }
}
