/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NetworkTypeValues.IPV4;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
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
import org.testcontainers.containers.GenericContainer;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractRedissonAsyncClientTest {

  @RegisterExtension
  protected static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private String ip;

  private int port;

  private String address;
  private RedissonClient redisson;

  @BeforeAll
  void setupAll() throws UnknownHostException {
    redisServer.start();
    ip = InetAddress.getByName(redisServer.getHost()).getHostAddress();
    port = redisServer.getMappedPort(6379);
    address = redisServer.getHost() + ":" + port;
  }

  @AfterAll
  void cleanupAll() {
    redisServer.stop();
  }

  @BeforeEach
  void setup() throws InvocationTargetException, IllegalAccessException {
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
    } catch (NoSuchMethodException ignored) {
      // ignored
    }
    redisson = Redisson.create(config);
    testing.clearData();
  }

  @AfterEach
  void cleanup() {
    if (redisson != null) {
      redisson.shutdown();
    }
  }

  @Test
  void futureSet() {
    RBucket<String> keyObject = redisson.getBucket("foo");
    RFuture<Void> future = keyObject.setAsync("bar");
    assertThat(future.toCompletableFuture()).succeedsWithin(TIMEOUT);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"))));
  }

  @Test
  void futureWhenComplete() {
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
    assertThat(result.toCompletableFuture()).succeedsWithin(TIMEOUT);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent", "SADD", "callback"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName("SADD")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SADD set1 ?"),
                            equalTo(maybeStable(DB_OPERATION), "SADD"))
                        .hasParent(trace.getSpan(0)),
                span -> span.hasName("callback").hasKind(INTERNAL).hasParent(trace.getSpan(0))));
  }

  // regression test for
  // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/6033
  @Test
  void scheduleCallable() throws ReflectiveOperationException {
    RScheduledExecutorService executorService = redisson.getExecutorService("EXECUTOR");
    //  Adapt different method signature:
    // `java.util.concurrent.ScheduledFuture<V> schedule(Callable,long,TimeUnit)` in 3.0.1
    // and `org.redisson.api.RScheduledFuture#schedule(java.lang.Runnable, long,TimeUnit)`
    // in other versions
    Object future = invokeSchedule(executorService);
    // In 3.0.1 getTaskId method doesn't exist in`ScheduledFuture` as it belongs to java.util.*
    // package,
    // but in RScheduledFuture that is an implementation of `ScheduledFuture`
    assertThat(future).isInstanceOf(RScheduledFuture.class);
    assertThat(((RScheduledFuture) future).getTaskId()).isNotBlank();
  }

  private static Object invokeSchedule(RScheduledExecutorService executorService)
      throws ReflectiveOperationException {
    return executorService
        .getClass()
        .getMethod("schedule", Callable.class, long.class, TimeUnit.class)
        .invoke(executorService, new MyCallable(), 0, SECONDS);
  }

  @Test
  void atomicBatchCommand() {
    try {
      // available since 3.7.2
      Class.forName("org.redisson.api.BatchOptions$ExecutionMode");
    } catch (ClassNotFoundException ignored) {
      Assumptions.abort();
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
    assertThat(result.toCompletableFuture()).succeedsWithin(TIMEOUT);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("parent", "SADD", "callback"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "redis" : "DB Query")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "MULTI;SET batch1 ?"))
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("SET")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET batch2 ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"))
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("EXEC")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "EXEC"),
                            equalTo(maybeStable(DB_OPERATION), "EXEC"))
                        .hasParent(trace.getSpan(0)),
                span -> span.hasName("callback").hasKind(INTERNAL).hasParent(trace.getSpan(0))));
  }

  protected boolean useRedisProtocol() {
    return testLatestDeps();
  }

  private static class MyCallable implements Serializable, Callable<Object> {
    private static final long serialVersionUID = 1L;

    @Override
    public Object call() throws Exception {
      return null;
    }
  }
}
