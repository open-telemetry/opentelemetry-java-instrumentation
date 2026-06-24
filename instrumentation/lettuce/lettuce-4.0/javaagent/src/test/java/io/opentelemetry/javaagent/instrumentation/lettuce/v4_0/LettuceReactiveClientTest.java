/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.lambdaworks.redis.ClientOptions;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.api.StatefulRedisConnection;
import com.lambdaworks.redis.api.rx.RedisReactiveCommands;
import com.lambdaworks.redis.api.sync.RedisCommands;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import rx.functions.Action1;
import rx.schedulers.Schedulers;

@SuppressWarnings("deprecation") // using deprecated semconv
class LettuceReactiveClientTest {
  private static final Logger logger = LoggerFactory.getLogger(LettuceReactiveClientTest.class);

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final DockerImageName CONTAINER_IMAGE =
      DockerImageName.parse("redis:6.2.3-alpine");

  private static final int DB_INDEX = 0;

  private static final ClientOptions CLIENT_OPTIONS =
      new ClientOptions.Builder().autoReconnect(false).build();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>(CONTAINER_IMAGE)
          .withExposedPorts(6379)
          .withLogConsumer(new Slf4jLogConsumer(logger))
          .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));

  private static RedisReactiveCommands<String, String> reactiveCommands;
  private static String host;

  @BeforeAll
  static void setUp() {
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);

    host = redisServer.getHost();
    int port = redisServer.getMappedPort(6379);
    String embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    RedisClient redisClient = RedisClient.create(embeddedDbUri);
    redisClient.setOptions(CLIENT_OPTIONS);
    cleanup.deferAfterAll(redisClient::shutdown);

    StatefulRedisConnection<String, String> connection = redisClient.connect();
    cleanup.deferAfterAll(connection);

    reactiveCommands = connection.reactive();
    RedisCommands<String, String> syncCommands = connection.sync();
    syncCommands.set("TESTKEY", "TESTVAL");

    // 1 set + 1 connect trace
    testing.waitForTraces(2);
    testing.clearData();
  }

  @Test
  void testSetCommandWithSubscribeOnDefinedConsumer()
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<String> future = new CompletableFuture<>();
    Action1<String> consumer =
        res ->
            testing.runWithSpan(
                "callback",
                () -> {
                  assertThat(res).isEqualTo("OK");
                  future.complete(res);
                });

    testing.runWithSpan(
        "parent",
        () ->
            reactiveCommands
                .set("REACTIVE_SET_KEY", "REACTIVE_SET_VAL")
                .subscribe(consumer, future::completeExceptionally));

    assertThat(future.get(10, SECONDS)).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(commandAttributes("SET")),
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
            },
            future::completeExceptionally);

    assertThat(future.get(10, SECONDS)).isEqualTo("TESTVAL");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(commandAttributes("GET"))));
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
        () ->
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
                            }),
                    future::completeExceptionally));

    assertThat(future.get(10, SECONDS)).isEqualTo(defaultVal);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(commandAttributes("GET")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void testCommandWithNoArguments()
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<String> future = new CompletableFuture<>();

    reactiveCommands.randomkey().subscribe(future::complete, future::completeExceptionally);

    assertThat(future.get(10, SECONDS)).isNotNull();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(commandAttributes("RANDOMKEY"))));
  }

  @Test
  void testCommandFluxPublisher()
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<Integer> future = new CompletableFuture<>();

    reactiveCommands.command().count().subscribe(future::complete, future::completeExceptionally);

    assertThat(future.get(10, SECONDS)).isGreaterThan(100);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("COMMAND")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(commandAttributes("COMMAND"))));
  }

  @Test
  void testCommandCancelAfter2OnFluxPublisher()
      throws ExecutionException, InterruptedException, TimeoutException {
    CompletableFuture<Integer> future = new CompletableFuture<>();

    reactiveCommands
        .command()
        .take(2)
        .count()
        .subscribe(future::complete, future::completeExceptionally);

    assertThat(future.get(10, SECONDS)).isEqualTo(2);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("COMMAND")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(commandAttributes("COMMAND"))));
  }

  @Test
  void testNonReactiveCommandShouldNotProduceSpan() {
    String res = reactiveCommands.digest(null);

    assertThat(res).isNotNull();
    assertThat(testing.spans()).isEmpty();
  }

  @Test
  void testShutdownCommandShouldProduceSpan() {
    // Test Causes redis to crash therefore it needs its own container
    StatefulRedisConnection<String, String> statefulConnection = newContainerConnection();
    cleanup.deferCleanup(statefulConnection);

    RedisReactiveCommands<String, String> commands = statefulConnection.reactive();
    commands.shutdown(false).subscribe();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SHUTDOWN")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(commandAttributes("SHUTDOWN"))));
  }

  @Test
  void testBlockingSubscriber() {
    String result =
        testing.runWithSpan(
            "test-parent",
            () -> {
              String setResult = reactiveCommands.set("a", "1").toBlocking().single();
              assertThat(setResult).isEqualTo("OK");
              return reactiveCommands.get("a").toBlocking().single();
            });

    assertThat(result).isEqualTo("1");
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test-parent").hasTotalAttributeCount(0),
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(commandAttributes("SET")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(commandAttributes("GET"))));
  }

  @Test
  void testAsyncSubscriber() {
    CompletableFuture<String> future = new CompletableFuture<>();

    testing.runWithSpan(
        "test-parent",
        () ->
            reactiveCommands
                .set("a", "1")
                .subscribe(
                    ignored ->
                        reactiveCommands
                            .get("a")
                            .subscribe(future::complete, future::completeExceptionally),
                    future::completeExceptionally));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test-parent").hasTotalAttributeCount(0),
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(commandAttributes("SET")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(commandAttributes("GET"))));
    assertThat(future).isCompletedWithValue("1");
  }

  @Test
  void testAsyncSubscriberWithSpecificThreadPool() {
    CompletableFuture<String> future = new CompletableFuture<>();

    testing.runWithSpan(
        "test-parent",
        () ->
            reactiveCommands
                .set("a", "1")
                .subscribeOn(Schedulers.io())
                .subscribe(
                    ignored ->
                        reactiveCommands
                            .get("a")
                            .subscribeOn(Schedulers.io())
                            .subscribe(future::complete, future::completeExceptionally),
                    future::completeExceptionally));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("test-parent").hasTotalAttributeCount(0),
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(commandAttributes("SET")),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(commandAttributes("GET"))));
    assertThat(future).isCompletedWithValue("1");
  }

  private static StatefulRedisConnection<String, String> newContainerConnection() {
    GenericContainer<?> server =
        new GenericContainer<>(CONTAINER_IMAGE)
            .withExposedPorts(6379)
            .withLogConsumer(new Slf4jLogConsumer(logger))
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
    server.start();
    cleanup.deferCleanup(server::stop);

    long serverPort = server.getMappedPort(6379);

    RedisClient client = RedisClient.create("redis://" + host + ":" + serverPort + "/" + DB_INDEX);
    client.setOptions(CLIENT_OPTIONS);
    cleanup.deferCleanup(client::shutdown);

    StatefulRedisConnection<String, String> statefulConnection = client.connect();

    // 1 connect trace
    testing.waitForTraces(1);
    testing.clearData();

    return statefulConnection;
  }

  private static AttributeAssertion[] commandAttributes(String operation) {
    return new AttributeAssertion[] {
      equalTo(maybeStable(DB_SYSTEM), REDIS), equalTo(maybeStable(DB_OPERATION), operation)
    };
  }
}
