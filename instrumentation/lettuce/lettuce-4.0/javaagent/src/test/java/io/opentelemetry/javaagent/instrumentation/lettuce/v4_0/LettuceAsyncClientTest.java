/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.google.common.collect.ImmutableMap;
import com.lambdaworks.redis.ClientOptions;
import com.lambdaworks.redis.RedisClient;
import com.lambdaworks.redis.RedisConnectionException;
import com.lambdaworks.redis.RedisFuture;
import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.api.StatefulRedisConnection;
import com.lambdaworks.redis.api.async.RedisAsyncCommands;
import com.lambdaworks.redis.api.sync.RedisCommands;
import com.lambdaworks.redis.codec.Utf8StringCodec;
import com.lambdaworks.redis.protocol.AsyncCommand;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class LettuceAsyncClientTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LoggerFactory.getLogger(LettuceAsyncClientTest.class);

  private static final int DB_INDEX = 0;

  // Disable auto reconnect, so we do not get stray traces popping up on server shutdown
  private static final ClientOptions CLIENT_OPTIONS =
      new ClientOptions.Builder().autoReconnect(false).build();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>(DockerImageName.parse("redis:6.2.3-alpine")).withExposedPorts(6379);

  private static String host;
  private static int port;
  private static int incorrectPort;
  private static String dbUriNonExistent;
  private static String embeddedDbUri;

  private static final ImmutableMap<String, String> testHashMap =
      ImmutableMap.of(
          "firstname", "John",
          "lastname", "Doe",
          "age", "53");

  static RedisClient redisClient;
  private static StatefulRedisConnection<String, String> connection;
  static RedisAsyncCommands<String, String> asyncCommands;
  static RedisCommands<String, String> syncCommands;

  @BeforeAll
  public static void setUp() {
    redisServer.start();
    host = redisServer.getHost();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    incorrectPort = PortUtils.findOpenPort();
    dbUriNonExistent = "redis://" + host + ":" + incorrectPort + "/" + DB_INDEX;

    redisClient = RedisClient.create(embeddedDbUri);
    redisClient.setOptions(CLIENT_OPTIONS);

    connection = redisClient.connect();
    asyncCommands = connection.async();
    syncCommands = connection.sync();

    syncCommands.set("TESTKEY", "TESTVAL");

    // 1 set + 1 connect trace
    testing.clearData();
  }

  @AfterAll
  public static void cleanUp() {
    connection.close();
    redisServer.stop();
  }

  @Test
  void testConnectUsingGetOnConnectionFuture() {
    RedisClient testConnectionClient = RedisClient.create(embeddedDbUri);
    testConnectionClient.setOptions(CLIENT_OPTIONS);

    StatefulRedisConnection<String, String> connection =
        testConnectionClient.connect(
            new Utf8StringCodec(), new RedisURI(host, port, 3, TimeUnit.SECONDS));

    assertThat(connection).isNotNull();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("CONNECT")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_PEER_NAME, host),
                            equalTo(SemanticAttributes.NET_PEER_PORT, port),
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"))));
    connection.close();
  }

  @Test
  void testExceptionInsideTheConnectionFuture() {
    RedisClient testConnectionClient = RedisClient.create(dbUriNonExistent);
    testConnectionClient.setOptions(CLIENT_OPTIONS);

    Exception exception =
        catchException(
            () -> {
              StatefulRedisConnection<String, String> connection =
                  testConnectionClient.connect(
                      new Utf8StringCodec(),
                      new RedisURI(host, incorrectPort, 3, TimeUnit.SECONDS));

              assertThat(connection).isNull();
            });

    assertThat(exception).isInstanceOf(RedisConnectionException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("CONNECT")
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.error())
                        .hasException(exception)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_PEER_NAME, host),
                            equalTo(SemanticAttributes.NET_PEER_PORT, incorrectPort),
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"))));
  }

  @Test
  void testSetCommandUsingFutureGetWithTimeout()
      throws ExecutionException, InterruptedException, TimeoutException {
    RedisFuture<String> redisFuture = asyncCommands.set("TESTSETKEY", "TESTSETVAL");
    String res = redisFuture.get(3, TimeUnit.SECONDS);

    assertThat(res).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_OPERATION, "SET"))));
  }

  @Test
  void testCommandChainedWithThenAccept() throws Throwable {
    AsyncConditions conditions = new AsyncConditions();
    Consumer<String> consumer =
        res ->
            testing.runWithSpan(
                "callback", () -> conditions.evaluate(() -> assertThat(res).isEqualTo("TESTVAL")));

    testing.runWithSpan(
        "parent",
        () -> {
          RedisFuture<String> redisFuture = asyncCommands.get("TESTKEY");
          redisFuture.thenAccept(consumer);
        });

    conditions.await(10);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_OPERATION, "GET")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  private static class AsyncConditions {
    private final int numEvalBlocks;
    private final CountDownLatch latch;
    private final ConcurrentLinkedQueue<Throwable> exceptions;

    public AsyncConditions() {
      this(1);
    }

    public AsyncConditions(int numEvalBlocks) {
      this.exceptions = new ConcurrentLinkedQueue<>();
      this.numEvalBlocks = numEvalBlocks;
      this.latch = new CountDownLatch(numEvalBlocks);
    }

    public void evaluate(Runnable block) {
      try {
        block.run();
      } catch (Throwable var3) {
        this.exceptions.add(var3);
        this.wakeUp();
      }

      this.latch.countDown();
    }

    private void wakeUp() {
      long pendingEvalBlocks = this.latch.getCount();

      for (int i = 0; (long) i < pendingEvalBlocks; ++i) {
        this.latch.countDown();
      }
    }

    public void await(double seconds) throws Throwable {
      this.latch.await((long) (seconds * 1000.0), TimeUnit.MILLISECONDS);
      if (!this.exceptions.isEmpty()) {
        throw this.exceptions.poll();
      } else {
        long pendingEvalBlocks = this.latch.getCount();
        if (pendingEvalBlocks > 0L) {
          String msg =
              String.format(
                  "Async conditions timed out after %1.2f seconds; %d out of %d evaluate blocks did not complete in time",
                  seconds, pendingEvalBlocks, this.numEvalBlocks);
          throw new TimeoutException(msg);
        }
      }
    }
  }

  // to make sure instrumentation's chained completion stages won't interfere with user's, while
  // still
  // recording metrics
  @Test
  void getNonExistentKeyCommandWithHandleAsyncAndChainedWithThenApply() throws Throwable {
    AsyncConditions conditions = new AsyncConditions();
    String successStr = "KEY MISSING";

    BiFunction<String, Throwable, String> firstStage =
        (res, error) -> {
          testing.runWithSpan(
              "callback1",
              () ->
                  conditions.evaluate(
                      () -> {
                        assertThat(res).isNull();
                        assertThat(error).isNull();
                      }));
          return (res == null ? successStr : res);
        };
    Function<String, Object> secondStage =
        input -> {
          testing.runWithSpan(
              "callback2",
              () ->
                  conditions.evaluate(
                      () -> {
                        assertThat(input).isEqualTo(successStr);
                      }));
          return null;
        };

    testing.runWithSpan(
        "parent",
        () -> {
          RedisFuture<String> redisFuture = asyncCommands.get("NON_EXISTENT_KEY");
          redisFuture.handle(firstStage).thenApply(secondStage);
        });

    conditions.await(10);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_OPERATION, "GET")),
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
  void testCommandWithNoArgumentsUsingABiconsumer() {
    AsyncConditions conditions = new AsyncConditions();
    BiConsumer<String, Throwable> biConsumer =
        (keyRetrieved, error) ->
            testing.runWithSpan(
                "callback", () -> conditions.evaluate(() -> assertThat(keyRetrieved).isNotNull()));

    testing.runWithSpan(
        "parent",
        () -> {
          RedisFuture<String> redisFuture = asyncCommands.randomkey();
          redisFuture.whenCompleteAsync(biConsumer);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                span ->
                    span.hasName("RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_OPERATION, "RANDOMKEY")),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void testHashSetAndThenNestApplyToHashGetall() throws Throwable {
    AsyncConditions conditions = new AsyncConditions();

    RedisFuture<String> hmsetFuture = asyncCommands.hmset("TESTHM", testHashMap);
    hmsetFuture.thenApplyAsync(
        setResult -> {
          // Wait for 'hmset' trace to get written
          testing.waitForTraces(1);

          conditions.evaluate(
              () -> {
                assertThat(setResult).isEqualTo("OK");
              });
          RedisFuture<Map<String, String>> hmGetAllFuture = asyncCommands.hgetall("TESTHM");
          hmGetAllFuture.exceptionally(
              error -> {
                logger.error("unexpected: {}", error.toString());
                return null;
              });
          hmGetAllFuture.thenAccept(
              hmGetAllResult ->
                  conditions.evaluate(() -> assertThat(testHashMap).isEqualTo(hmGetAllResult)));
          return null;
        });

    conditions.await(10);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("HMSET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_OPERATION, "HMSET"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("HGETALL")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_OPERATION, "HGETALL"))));
  }

  @Test
  void testCommandCompletesExceptionally() throws Throwable {
    // turn off auto flush to complete the command exceptionally manually
    asyncCommands.setAutoFlushCommands(false);

    AsyncConditions conditions = new AsyncConditions();

    RedisFuture<Long> redisFuture = asyncCommands.del("key1", "key2");
    boolean completedExceptionally =
        ((AsyncCommand<?, ?, ?>) redisFuture)
            .completeExceptionally(new IllegalStateException("TestException"));

    redisFuture.exceptionally(
        error -> {
          conditions.evaluate(
              () -> {
                assertThat(error).isNotNull();
                assertThat(error).isInstanceOf(IllegalStateException.class);
                assertThat(error.getMessage()).isEqualTo("TestException");
              });
          throw new RuntimeException(error);
        });

    asyncCommands.flushCommands();
    Throwable thrown = catchThrowable(redisFuture::get);

    conditions.await(10);
    assertThat(completedExceptionally).isTrue();
    assertThat(thrown).isInstanceOf(Exception.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DEL")
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.error())
                        .hasException(new IllegalStateException("TestException"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_OPERATION, "DEL"))));
    // set this back so other tests don't fail
    asyncCommands.setAutoFlushCommands(true);
  }

  @Test
  void testCommandBeforeItFinished() throws Throwable {
    asyncCommands.setAutoFlushCommands(false);
    AsyncConditions conditions = new AsyncConditions();

    RedisFuture<Long> redisFuture =
        testing.runWithSpan("parent", () -> asyncCommands.sadd("SKEY", "1", "2"));
    redisFuture.whenCompleteAsync(
        (res, error) ->
            testing.runWithSpan(
                "callback",
                () ->
                    conditions.evaluate(
                        () -> {
                          assertThat(error).isNotNull();
                          assertThat(error).isInstanceOf(CancellationException.class);
                        })));

    boolean cancelSuccess = redisFuture.cancel(true);
    asyncCommands.flushCommands();

    conditions.await(10);

    assertThat(cancelSuccess).isTrue();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("parent")
                        .hasKind(SpanKind.INTERNAL)
                        .hasNoParent()
                        .hasAttributes(Attributes.empty()),
                span ->
                    span.hasName("SADD")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_OPERATION, "SADD"),
                            equalTo(booleanKey("lettuce.command.cancelled"), true)),
                span ->
                    span.hasName("callback")
                        .hasKind(SpanKind.INTERNAL)
                        .hasParent(trace.getSpan(0))));
    // set this back so other tests don't fail
    asyncCommands.setAutoFlushCommands(true);
  }

  @Test
  void testDebugSegfaultCommandWithNoArgumentShouldProduceSpan() {
    // Test Causes redis to crash therefore it needs its own container
    GenericContainer<?> server =
        new GenericContainer<>(DockerImageName.parse("redis:6.2.3-alpine")).withExposedPorts(6379);
    server.start();

    long serverPort = server.getMappedPort(6379);
    RedisClient client = RedisClient.create("redis://" + host + ":" + serverPort + "/" + DB_INDEX);
    StatefulRedisConnection<String, String> connection1 = client.connect();
    RedisAsyncCommands<String, String> commands = connection1.async();
    // 1 connect trace
    testing.clearData();

    commands.debugSegfault();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DEBUG")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_OPERATION, "DEBUG"))));

    // Server already crashed but just in case
    connection1.close();
    server.stop();
  }

  @Test
  void testShutdownCommandShouldProduceASpan() {
    // Test Causes redis to crash therefore it needs its own container
    GenericContainer<?> server =
        new GenericContainer<>(DockerImageName.parse("redis:6.2.3-alpine")).withExposedPorts(6379);
    server.start();

    long shutdownServerPort = server.getMappedPort(6379);

    RedisClient client =
        RedisClient.create("redis://" + host + ":" + shutdownServerPort + "/" + DB_INDEX);
    StatefulRedisConnection<String, String> connection1 = client.connect();
    RedisAsyncCommands<String, String> commands = connection1.async();
    // 1 connect trace
    testing.clearData();

    commands.shutdown(false);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SHUTDOWN")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_OPERATION, "SHUTDOWN"))));
    // Server already crashed but just in case
    connection1.close();
    server.stop();
  }
}
