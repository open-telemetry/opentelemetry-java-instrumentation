/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.SemanticAttributes;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Assume;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.redisson.Redisson;
import org.redisson.api.BatchOptions;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RFuture;
import org.redisson.api.RScheduledExecutorService;
import org.redisson.api.RScheduledFuture;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SuppressWarnings("deprecation") // until old http semconv are dropped in 2.0
public abstract class AbstractRedissonAsyncClientTest {

  private static final Logger logger =
      LoggerFactory.getLogger(AbstractRedissonAsyncClientTest.class);

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private static int port;

  private static String address;
  private static RedissonClient redisson;

  @BeforeAll
  static void setupAll() {
    redisServer.start();
    port = redisServer.getMappedPort(6379);
    address = "localhost:" + port;
  }

  @AfterAll
  static void cleanupAll() {
    redisson.shutdown();
    redisServer.stop();
  }

  @BeforeEach
  void setup() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    String newAddress = address;
    if (useRedisProtocol()) {
      // Newer versions of redisson require scheme, older versions forbid it
      newAddress = "redis://" + address;
    }
    Config config = new Config();
    SingleServerConfig singleServerConfig = config.useSingleServer();
    singleServerConfig.setAddress(newAddress);
    singleServerConfig.setTimeout(30_000);
    try {
      // disable connection ping if it exists
      singleServerConfig
          .getClass()
          .getMethod("setPingConnectionInterval", int.class)
          .invoke(singleServerConfig, 0);
    } catch (NoSuchMethodException e) {
      logger.warn("no setPingConnectionInterval method", e);
    }
    redisson = Redisson.create(config);
    testing.clearData();
  }

  @Test
  void futureSet() throws ExecutionException, InterruptedException, TimeoutException {
    RBucket<String> keyObject = redisson.getBucket("foo");
    RFuture<Void> future = keyObject.setAsync("bar");
    future.get(30, TimeUnit.SECONDS);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, (long) port),
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "SET foo ?"),
                            equalTo(SemanticAttributes.DB_OPERATION, "SET"))));
  }

  @Test
  void futureWhenComplete() throws ExecutionException, InterruptedException, TimeoutException {
    RSet<String> set = redisson.getSet("set1");
    CompletionStage<Boolean> result =
        testing.runWithSpan(
            "parent",
            () -> {
              RFuture<Boolean> future = set.addAsync("s1");
              return future.whenComplete(
                  (res, throwable) -> {
                    assertThat(Span.current().getSpanContext().isValid()).isTrue();
                    testing.runWithSpan("callback", () -> {});
                  });
            });
    result.toCompletableFuture().get(30, TimeUnit.SECONDS);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent", "SADD", "callback"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SADD")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, (long) port),
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "SADD set1 ?"),
                            equalTo(SemanticAttributes.DB_OPERATION, "SADD"))
                        .hasParent(trace.getSpan(0)),
                span -> span.hasName("callback").hasKind(INTERNAL).hasParent(trace.getSpan(0))));
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/6033
  @Test
  void scheduleCallable()
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    RScheduledExecutorService executorService = redisson.getExecutorService("EXECUTOR");
    //  Adapt different method signature:
    // `java.util.concurrent.ScheduledFuture<V> schedule(Callable,long,TimeUnit)` in 3.0.1
    // and `org.redisson.api.RScheduledFuture#schedule(java.lang.Runnable, long,TimeUnit)`
    // in other versions
    Object future = invokeSchedule(executorService);
    // In 3.0.1 getTaskId method doesn't exist in`ScheduledFuture` as it belongs to java.util.*
    // package,
    // but in RScheduledFuture that is an implementation of `ScheduledFuture`
    assertThat(future instanceof RScheduledFuture).isTrue();
    assertThat(((RScheduledFuture) future).getTaskId()).isNotBlank();
  }

  private static Object invokeSchedule(RScheduledExecutorService executorService)
      throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    return executorService
        .getClass()
        .getMethod("schedule", Callable.class, long.class, TimeUnit.class)
        .invoke(executorService, new MyCallable(), 0, TimeUnit.SECONDS);
  }

  @Test
  void atomicBatchCommand() throws ExecutionException, InterruptedException, TimeoutException {
    try {
      // available since 3.7.2
      Class.forName("org.redisson.api.BatchOptions$ExecutionMode");
    } catch (ClassNotFoundException exception) {
      Assume.assumeNoException(exception);
    }
    // Don't specify explicit generic type, because `BatchResult` not exist in some versions.
    CompletionStage<?> result =
        testing.runWithSpan(
            "parent",
            () -> {
              BatchOptions batchOptions =
                  BatchOptions.defaults()
                      .executionMode(BatchOptions.ExecutionMode.REDIS_WRITE_ATOMIC);
              RBatch batch = redisson.createBatch(batchOptions);
              batch.getBucket("batch1").setAsync("v1");
              batch.getBucket("batch2").setAsync("v2");
              RFuture<?> batchResultFuture = batch.executeAsync();

              return batchResultFuture.whenComplete(
                  (res, throwable) -> {
                    assertThat(Span.current().getSpanContext().isValid()).isTrue();
                    testing.runWithSpan("callback", () -> {});
                  });
            });
    result.toCompletableFuture().get(30, TimeUnit.SECONDS);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent", "SADD", "callback"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("DB Query")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, (long) port),
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "MULTI;SET batch1 ?"))
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("SET")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, (long) port),
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "SET batch2 ?"),
                            equalTo(SemanticAttributes.DB_OPERATION, "SET"))
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("EXEC")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_NAME, "localhost"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, (long) port),
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "EXEC"),
                            equalTo(SemanticAttributes.DB_OPERATION, "EXEC"))
                        .hasParent(trace.getSpan(0)),
                span -> span.hasName("callback").hasKind(INTERNAL).hasParent(trace.getSpan(0))));
  }

  protected boolean useRedisProtocol() {
    return Boolean.getBoolean("testLatestDeps");
  }

  private static class MyCallable implements Serializable, Callable<Object> {
    private static final long serialVersionUID = 1L;

    @Override
    public Object call() throws Exception {
      return null;
    }
  }
}
