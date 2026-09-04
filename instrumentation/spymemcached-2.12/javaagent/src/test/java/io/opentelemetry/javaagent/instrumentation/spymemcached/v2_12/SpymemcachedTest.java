/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.MEMCACHED;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.spy.memcached.ConnectionFactoryBuilder.Protocol.BINARY;
import static net.spy.memcached.ConnectionFactoryBuilder.Protocol.TEXT;
import static net.spy.memcached.FailureMode.Redistribute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.google.common.util.concurrent.MoreExecutors;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.ConnectionFactory;
import net.spy.memcached.ConnectionFactoryBuilder;
import net.spy.memcached.DefaultConnectionFactory;
import net.spy.memcached.FailureMode;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;
import net.spy.memcached.NodeLocator;
import net.spy.memcached.internal.BulkFuture;
import net.spy.memcached.internal.CheckedOperationTimeoutException;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.ops.KeyedOperation;
import net.spy.memcached.ops.Operation;
import net.spy.memcached.ops.OperationQueueFactory;
import net.spy.memcached.ops.OperationState;
import net.spy.memcached.protocol.BaseOperationImpl;
import net.spy.memcached.protocol.ascii.AsciiMemcachedNodeImpl;
import net.spy.memcached.protocol.binary.MultiGetOperationImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

@SuppressWarnings("deprecation") // using deprecated semconv
class SpymemcachedTest {
  private static final String KEY_PREFIX = "SpymemcachedTest-";
  // https://github.com/memcached/memcached/wiki/Programming#expiration
  private static final int EXPIRATION_SECONDS = 3600;
  private static final int TIMING_OUT_OPERATION_TIMEOUT_MILLIS = 1000;

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  static GenericContainer<?> memcachedContainer;
  static InetSocketAddress memcachedAddress;
  static GenericContainer<?> secondMemcachedContainer;
  static InetSocketAddress secondMemcachedAddress;

  private static final boolean EXPERIMENTAL_ATTRIBUTES =
      Boolean.getBoolean("otel.instrumentation.spymemcached.experimental-span-attributes");

  @BeforeAll
  static void setUp() {
    memcachedContainer =
        new GenericContainer<>("memcached:1.6.41")
            .withExposedPorts(11211)
            .withStartupTimeout(Duration.ofMinutes(2));
    memcachedContainer.start();
    cleanup.deferAfterAll(memcachedContainer::stop);
    memcachedAddress =
        new InetSocketAddress(
            memcachedContainer.getHost(), memcachedContainer.getMappedPort(11211));

    secondMemcachedContainer =
        new GenericContainer<>("memcached:1.6.41")
            .withExposedPorts(11211)
            .withStartupTimeout(Duration.ofMinutes(2));
    secondMemcachedContainer.start();
    cleanup.deferAfterAll(secondMemcachedContainer::stop);
    secondMemcachedAddress =
        new InetSocketAddress(
            secondMemcachedContainer.getHost(), secondMemcachedContainer.getMappedPort(11211));
  }

  private static MemcachedClient getMemcached() {
    return getMemcached(emptyMap(), builder -> {});
  }

  private static MemcachedClient getMemcached(Map<String, String> testData) {
    return getMemcached(testData, builder -> {});
  }

  private static MemcachedClient getMemcached(
      Map<String, String> testData, Consumer<ConnectionFactoryBuilder> customizer) {
    // Use direct executor service so our listeners finish in deterministic order
    ExecutorService listenerExecutorService = MoreExecutors.newDirectExecutorService();

    ConnectionFactoryBuilder connectionFactoryBuilder =
        new ConnectionFactoryBuilder()
            .setListenerExecutorService(listenerExecutorService)
            .setProtocol(BINARY);
    customizer.accept(connectionFactoryBuilder);

    ConnectionFactory connectionFactory = connectionFactoryBuilder.build();
    try {
      MemcachedClient memcached =
          new MemcachedClient(connectionFactory, singletonList(memcachedAddress));
      cleanup.deferCleanup(memcached::shutdown);

      testing.runWithSpan(
          "setup",
          () -> {
            for (Map.Entry<String, String> entry : testData.entrySet()) {
              if (!memcached.set(key(entry.getKey()), EXPIRATION_SECONDS, entry.getValue()).get()) {
                throw new IllegalStateException("Failed to set key " + entry.getKey());
              }
            }
          });
      testing.waitForTraces(1);
      testing.clearData();

      return memcached;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private static MemcachedClient getMemcached(List<InetSocketAddress> nodes) {
    return getMemcached(nodes, builder -> {});
  }

  private static MemcachedClient getMemcached(
      List<InetSocketAddress> nodes, Consumer<ConnectionFactoryBuilder> customizer) {
    ConnectionFactoryBuilder connectionFactoryBuilder =
        new ConnectionFactoryBuilder()
            .setListenerExecutorService(MoreExecutors.newDirectExecutorService())
            .setProtocol(BINARY);
    customizer.accept(connectionFactoryBuilder);
    ConnectionFactory connectionFactory = connectionFactoryBuilder.build();
    try {
      MemcachedClient memcached = new MemcachedClient(connectionFactory, nodes);
      cleanup.deferCleanup(memcached::shutdown);
      return memcached;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void getDurationMetric() {
    MemcachedClient memcached = getMemcached(singletonMap("test-get", "get test"));
    testing.runWithSpan(
        "parent", () -> assertThat(memcached.get(key("test-get"))).isEqualTo("get test"));

    assertDurationMetric(
        testing,
        "io.opentelemetry.spymemcached-2.12",
        DB_SYSTEM_NAME,
        maybeStable(DB_OPERATION),
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @Test
  void getHit() {
    MemcachedClient memcached = getMemcached(singletonMap("test-get", "get test"));
    testing.runWithSpan(
        "parent", () -> assertThat(memcached.get(key("test-get"))).isEqualTo("get test"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void getMiss() {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent", () -> assertThat(memcached.get(key("test-get-key-that-doesn't-exist"))).isNull());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("miss")))));
  }

  @Test
  void getCancel() {
    ReentrantLock queueLock = new ReentrantLock();
    OperationQueueFactory lockableQueueFactory = () -> getLockableQueue(queueLock);
    MemcachedClient lockableMemcached =
        getMemcached(
            singletonMap("test-get", "get test"),
            builder -> builder.setOpQueueFactory(lockableQueueFactory));
    testing.runWithSpan(
        "parent",
        () -> {
          queueLock.lock();
          try {
            lockableMemcached.asyncGet(key("test-get")).cancel(true);
          } finally {
            queueLock.unlock();
          }
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(
                                booleanKey("spymemcached.command.cancelled"),
                                experimental(true)))));
  }

  @Test
  void getTimeout() throws InterruptedException {
    /*
    Not using runWithSpan since timeouts happen in separate thread
    and direct executor doesn't help to make sure that parent span finishes last.
    Instead run without parent span to have only 1 span to test with.
     */
    ReentrantLock queueLock = new ReentrantLock();
    OperationQueueFactory lockableQueueFactory = () -> getLockableQueue(queueLock);
    MemcachedClient timingOutMemcached =
        getMemcached(
            singletonMap("test-get", "get test"),
            builder ->
                builder
                    .setOpQueueFactory(lockableQueueFactory)
                    .setOpTimeout(TIMING_OUT_OPERATION_TIMEOUT_MILLIS));
    queueLock.lock();
    try {
      timingOutMemcached.asyncGet(key("test-get"));
      Thread.sleep(TIMING_OUT_OPERATION_TIMEOUT_MILLIS + 1000);
    } finally {
      queueLock.unlock();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            EXCEPTION_TYPE,
                                            CheckedOperationTimeoutException.class.getName()),
                                        equalTo(
                                            EXCEPTION_MESSAGE,
                                            "Operation timed out. - failing node: "
                                                + memcachedAddress),
                                        satisfies(
                                            EXCEPTION_STACKTRACE,
                                            val -> val.isInstanceOf(String.class))))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                ERROR_TYPE,
                                emitStableDatabaseSemconv()
                                    ? "net.spy.memcached.internal.CheckedOperationTimeoutException"
                                    : null),
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void bulkGet() {
    Map<String, String> testData = new HashMap<>();
    testData.put("test-get", "get test");
    testData.put("test-get-2", "get test 2");
    MemcachedClient memcached = getMemcached(testData);
    Map<String, Object> result =
        testing.runWithSpan("parent", () -> memcached.getBulk(key("test-get"), key("test-get-2")));
    assertThat(result)
        .hasSize(2)
        .containsEntry(key("test-get"), "get test")
        .containsEntry(key("test-get-2"), "get test 2");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName(emitStableDatabaseSemconv() ? "get" : "getBulk"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? "get" : "getBulk"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void set() throws Exception {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.set(key("test-set"), EXPIRATION_SECONDS, "bar").get()).isTrue();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("set"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "set"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void setCancel() {
    ReentrantLock queueLock = new ReentrantLock();
    OperationQueueFactory lockableQueueFactory = () -> getLockableQueue(queueLock);
    MemcachedClient lockableMemcached =
        getMemcached(emptyMap(), builder -> builder.setOpQueueFactory(lockableQueueFactory));
    testing.runWithSpan(
        "parent",
        () -> {
          queueLock.lock();
          try {
            assertThat(
                    lockableMemcached
                        .set(key("test-set-cancel"), EXPIRATION_SECONDS, "bar")
                        .cancel())
                .isTrue();
          } finally {
            queueLock.unlock();
          }
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("set"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "set"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(
                                booleanKey("spymemcached.command.cancelled"),
                                experimental(true)))));
  }

  @Test
  void add() throws Exception {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.add(key("test-add"), EXPIRATION_SECONDS, "add bar").get()).isTrue();
          assertThat(memcached.get(key("test-add"))).isEqualTo("add bar");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("add"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "add"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void secondAdd() throws Exception {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.add(key("test-add2"), EXPIRATION_SECONDS, "add bar").get()).isTrue();
          assertThat(memcached.add(key("test-add2"), EXPIRATION_SECONDS, "add bar 123").get())
              .isFalse();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("add"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "add"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("add"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "add"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void delete() throws Exception {
    MemcachedClient memcached = getMemcached(singletonMap("test-delete", "delete test"));
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.delete(key("test-delete")).get()).isTrue();
          assertThat(memcached.get(key("test-delete"))).isNull();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("delete"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "delete"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("miss")))));
  }

  @Test
  void deleteNonExistent() throws Exception {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.delete(key("test-delete-non-existent")).get()).isFalse();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("delete"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "delete"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void replace() throws Exception {
    MemcachedClient memcached = getMemcached(singletonMap("test-replace", "replace test"));
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.replace(key("test-replace"), EXPIRATION_SECONDS, "new value").get())
              .isTrue();
          assertThat(memcached.get(key("test-replace"))).isEqualTo("new value");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("replace"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "replace"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void replaceNonExistent() throws Exception {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(
                  memcached
                      .replace(key("test-replace-non-existent"), EXPIRATION_SECONDS, "new value")
                      .get())
              .isFalse();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("replace"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "replace"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void append() throws Exception {
    MemcachedClient memcached = getMemcached(singletonMap("test-append", "append test"));
    testing.runWithSpan(
        "parent",
        () -> {
          CASValue<Object> casValue = memcached.gets(key("test-append"));
          assertThat(memcached.append(casValue.getCas(), key("test-append"), " appended").get())
              .isTrue();
          assertThat(memcached.get(key("test-append"))).isEqualTo("append test appended");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("gets"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "gets"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("append"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "append"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void prepend() throws Exception {
    MemcachedClient memcached = getMemcached(singletonMap("test-prepend", "prepend test"));
    testing.runWithSpan(
        "parent",
        () -> {
          CASValue<Object> casValue = memcached.gets(key("test-prepend"));
          assertThat(memcached.prepend(casValue.getCas(), key("test-prepend"), "prepended ").get())
              .isTrue();
          assertThat(memcached.get(key("test-prepend"))).isEqualTo("prepended prepend test");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("gets"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "gets"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("prepend"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "prepend"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void cas() {
    MemcachedClient memcached = getMemcached(singletonMap("test-cas", "cas test"));
    testing.runWithSpan(
        "parent",
        () -> {
          CASValue<Object> casValue = memcached.gets(key("test-cas"));
          assertThat(
                  memcached.cas(key("test-cas"), casValue.getCas(), EXPIRATION_SECONDS, "cas bar"))
              .isEqualTo(CASResponse.OK);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("gets"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "gets"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("cas"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "cas"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void casNotFound() {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(
                  memcached.cas(key("test-cas-doesnt-exist"), 1234, EXPIRATION_SECONDS, "cas bar"))
              .isEqualTo(CASResponse.NOT_FOUND);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("cas"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "cas"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void touch() throws Exception {
    MemcachedClient memcached = getMemcached(singletonMap("test-touch", "touch test"));
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.touch(key("test-touch"), EXPIRATION_SECONDS).get()).isTrue();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("touch"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "touch"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void touchNonExistent() throws Exception {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.touch(key("test-touch-non-existent"), EXPIRATION_SECONDS).get())
              .isFalse();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("touch"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "touch"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void getAndTouch() {
    MemcachedClient memcached = getMemcached(singletonMap("test-touch", "touch test"));
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.getAndTouch(key("test-touch"), EXPIRATION_SECONDS).getValue())
              .isEqualTo("touch test");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName(emitStableDatabaseSemconv() ? "gat" : "getAndTouch"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? "gat" : "getAndTouch"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void getAndTouchNonExistent() {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.getAndTouch(key("test-touch-non-existent"), EXPIRATION_SECONDS))
              .isNull();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName(emitStableDatabaseSemconv() ? "gat" : "getAndTouch"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? "gat" : "getAndTouch"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void decr() {
    MemcachedClient memcached = getMemcached(singletonMap("test-decr", "200"));
    testing.runWithSpan(
        "parent",
        () -> {
          /*
           Memcached is funny in the way it handles incr/decr operations:
           it needs values to be strings (with digits in them) and it returns actual long from decr/incr
          */
          assertThat(memcached.decr(key("test-decr"), 5)).isEqualTo(195);
          assertThat(memcached.get(key("test-decr"))).isEqualTo("195");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("decr"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "decr"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void decrNonExistent() {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.decr(key("test-decr-non-existent"), 5)).isEqualTo(-1);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("decr"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "decr"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void decrException() {
    MemcachedClient memcached = getMemcached();
    assertThatThrownBy(() -> memcached.decr(key("long key: " + longString()), 5))
        .isInstanceOf(IllegalArgumentException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(spanName("decr"))
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(
                            new IllegalArgumentException("Key is too long (maxlen = 250)"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                ERROR_TYPE,
                                emitStableDatabaseSemconv()
                                    ? "java.lang.IllegalArgumentException"
                                    : null),
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "decr"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getHostString()
                                    : null),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null))));
  }

  @Test
  void incr() {
    MemcachedClient memcached = getMemcached(singletonMap("test-incr", "100"));
    testing.runWithSpan(
        "parent",
        () -> {
          /*
           Memcached is funny in the way it handles incr/decr operations:
           it needs values to be strings (with digits in them) and it returns actual long from decr/incr
          */
          assertThat(memcached.incr(key("test-incr"), 5)).isEqualTo(105);
          assertThat(memcached.get(key("test-incr"))).isEqualTo("105");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("incr"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "incr"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void incrNonExistent() {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.incr(key("test-incr-non-existent"), 5)).isEqualTo(-1);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("incr"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "incr"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void incrException() {
    MemcachedClient memcached = getMemcached();
    assertThatThrownBy(() -> memcached.incr(key("long key: " + longString()), 5))
        .isInstanceOf(IllegalArgumentException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(spanName("incr"))
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasStatus(StatusData.error())
                        .hasException(
                            new IllegalArgumentException("Key is too long (maxlen = 250)"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(
                                ERROR_TYPE,
                                emitStableDatabaseSemconv()
                                    ? "java.lang.IllegalArgumentException"
                                    : null),
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "incr"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getHostString()
                                    : null),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null))));
  }

  @Test
  void optimizedRetryUsesReassignedNode() throws Exception {
    InetSocketAddress retryContainerAddress = startAdditionalMemcached();

    ReentrantLock queueLock = new ReentrantLock();
    OperationQueueFactory lockableQueueFactory = () -> getLockableQueue(queueLock);
    MemcachedClient memcached =
        getMemcached(
            asList(memcachedAddress, retryContainerAddress),
            builder ->
                builder
                    .setProtocol(TEXT)
                    .setFailureMode(Redistribute)
                    .setOpQueueFactory(lockableQueueFactory));
    MemcachedConnection connection = memcached.getConnection();
    List<MemcachedNode> nodes = new ArrayList<>(connection.getLocator().getAll());
    assertThat(nodes).hasSize(2);
    MemcachedNode failedNode = connection.getLocator().getPrimary(key("optimized-retry-0"));
    MemcachedNode retryNode = nodes.get(0) == failedNode ? nodes.get(1) : nodes.get(0);
    InetSocketAddress retryAddress = (InetSocketAddress) retryNode.getSocketAddress();
    List<String> keys = keysForNode(connection, failedNode, 2);

    List<GetFuture<Object>> futures;
    queueLock.lock();
    try {
      futures =
          testing.runWithSpan(
              "parent",
              () -> asList(memcached.asyncGet(keys.get(0)), memcached.asyncGet(keys.get(1))));
      Collection<Operation> operations = failedNode.destroyInputQueue();
      assertThat(operations).hasSize(2);

      ConnectionFactory optimizerFactory = new ConnectionFactoryBuilder().setProtocol(TEXT).build();
      Operation optimizedOperation;
      try (SocketChannel channel = SocketChannel.open()) {
        AsciiMemcachedNodeImpl optimizer =
            new AsciiMemcachedNodeImpl(
                InetSocketAddress.createUnresolved("optimizer", 11211),
                channel,
                optimizerFactory.getReadBufSize(),
                optimizerFactory.createReadOperationQueue(),
                optimizerFactory.createWriteOperationQueue(),
                optimizerFactory.createOperationQueue(),
                optimizerFactory.getOpQueueMaxBlockTime(),
                optimizerFactory.getOperationTimeout(),
                optimizerFactory.getAuthWaitTime(),
                optimizerFactory);
        for (Operation operation : operations) {
          optimizer.addOp(operation);
        }
        optimizer.copyInputQueue();
        Method optimize = AsciiMemcachedNodeImpl.class.getDeclaredMethod("optimize");
        optimize.setAccessible(true);
        optimize.invoke(optimizer);
        optimizedOperation = optimizer.getCurrentWriteOp();
      }
      assertThat(((KeyedOperation) optimizedOperation).getKeys())
          .containsExactlyInAnyOrderElementsOf(keys);
      failedNode.reconnecting();
      connection.redistributeOperation(optimizedOperation);
    } finally {
      queueLock.unlock();
    }

    for (GetFuture<Object> future : futures) {
      assertThat(future.get()).isNull();
    }

    String target = configuredTarget(asList(memcachedAddress, retryContainerAddress));
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("get", target))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? target
                                    : retryAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? retryAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) retryAddress.getPort() : null),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : (long) retryAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("miss"))),
                span ->
                    span.hasName(spanName("get", target))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? target
                                    : retryAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? retryAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) retryAddress.getPort() : null),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : (long) retryAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("miss")))));
  }

  @Test
  void sequentialSingleKeyRetriesUseLastNode() throws Exception {
    InetSocketAddress retryContainerAddress = startAdditionalMemcached();
    List<InetSocketAddress> configuredNodes =
        asList(memcachedAddress, secondMemcachedAddress, retryContainerAddress);
    ReentrantLock queueLock = new ReentrantLock();
    OperationQueueFactory lockableQueueFactory = () -> getLockableQueue(queueLock);
    MemcachedClient memcached =
        getMemcached(
            configuredNodes,
            builder ->
                builder
                    .setProtocol(TEXT)
                    .setFailureMode(Redistribute)
                    .setOpQueueFactory(lockableQueueFactory));
    MemcachedConnection connection = memcached.getConnection();
    waitForNodes(connection);
    List<MemcachedNode> nodes = new ArrayList<>(connection.getLocator().getAll());
    String requestKey = key("sequential-retry");
    MemcachedNode initialNode = connection.getLocator().getPrimary(requestKey);
    InetSocketAddress finalAddress;

    GetFuture<Object> future;
    queueLock.lock();
    try {
      future = testing.runWithSpan("parent", () -> memcached.asyncGet(requestKey));
      Operation initialOperation = onlyOperation(initialNode.destroyInputQueue());
      markForRetry(initialOperation);
      initialNode.reconnecting();
      connection.redistributeOperation(initialOperation);

      QueuedOperation secondOperation = onlyQueuedOperation(nodes, initialNode);
      markForRetry(secondOperation.operation);
      secondOperation.node.reconnecting();
      MemcachedNode finalNode = nextNode(connection, requestKey, initialNode, secondOperation.node);
      finalAddress = (InetSocketAddress) finalNode.getSocketAddress();
      connection.redistributeOperation(secondOperation.operation);
    } finally {
      queueLock.unlock();
    }

    assertThat(future.get()).isNull();

    String target = configuredTarget(configuredNodes);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("get", target))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? target
                                    : finalAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? finalAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) finalAddress.getPort() : null),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv() ? null : (long) finalAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("miss")))));
  }

  @Test
  void partialBulkRetryUsesRetryNode() throws Exception {
    InetSocketAddress retryContainerAddress = startAdditionalMemcached();
    List<InetSocketAddress> configuredNodes = asList(memcachedAddress, retryContainerAddress);
    ReentrantLock queueLock = new ReentrantLock();
    OperationQueueFactory lockableQueueFactory = () -> getLockableQueue(queueLock);
    MemcachedClient memcached =
        getMemcached(
            configuredNodes,
            builder ->
                builder.setFailureMode(Redistribute).setOpQueueFactory(lockableQueueFactory));
    MemcachedConnection connection = memcached.getConnection();
    waitForNodes(connection);
    List<MemcachedNode> nodes = new ArrayList<>(connection.getLocator().getAll());
    MemcachedNode initialNode = nodes.get(0);
    List<String> keys = keysForNode(connection, initialNode, 2);
    String retryKey = keys.get(1);
    MemcachedNode retryNode = nextNode(connection, retryKey, initialNode);
    InetSocketAddress retryAddress = (InetSocketAddress) retryNode.getSocketAddress();

    BulkFuture<Map<String, Object>> future;
    queueLock.lock();
    try {
      future = testing.runWithSpan("parent", () -> memcached.asyncGetBulk(keys));
      Operation initialOperation = onlyOperation(initialNode.destroyInputQueue());
      assertThat(initialOperation).isInstanceOf(MultiGetOperationImpl.class);
      ((MultiGetOperationImpl) initialOperation).getRetryKeys().add(retryKey);
      markForRetry(initialOperation);
      initialNode.reconnecting();
      connection.redistributeOperation(initialOperation);
    } finally {
      queueLock.unlock();
    }

    assertThat(future.get()).isEmpty();

    String target = configuredTarget(configuredNodes);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName(emitStableDatabaseSemconv() ? "get" : "getBulk", target))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? "get" : "getBulk"),
                            equalTo(
                                SERVER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? target
                                    : retryAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? retryAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv() ? (long) retryAddress.getPort() : null),
                            equalTo(
                                SERVER_PORT,
                                emitStableDatabaseSemconv()
                                    ? null
                                    : (long) retryAddress.getPort()))));
  }

  @Test
  void multiKeyRetryAcrossSeveralNodesHasNoPeer() throws Exception {
    InetSocketAddress retryContainerAddress = startAdditionalMemcached();
    List<InetSocketAddress> configuredNodes =
        asList(memcachedAddress, secondMemcachedAddress, retryContainerAddress);
    ReentrantLock queueLock = new ReentrantLock();
    MemcachedClient memcached =
        new MemcachedClient(new FanoutConnectionFactory(queueLock), configuredNodes);
    cleanup.deferCleanup(memcached::shutdown);
    MemcachedConnection connection = memcached.getConnection();
    waitForNodes(connection);
    MemcachedNode failedNode = new ArrayList<>(connection.getLocator().getAll()).get(0);
    List<String> keys = asList(key("multi-node-retry-one"), key("multi-node-retry-two"));

    BulkFuture<Map<String, Object>> future;
    queueLock.lock();
    try {
      future = testing.runWithSpan("parent", () -> memcached.asyncGetBulk(keys));
      Operation operation = onlyOperation(failedNode.destroyInputQueue());
      markForRetry(operation);
      failedNode.reconnecting();
      connection.redistributeOperation(operation);
    } finally {
      queueLock.unlock();
    }

    assertThat(future.get()).isEmpty();

    String target = configuredTarget(configuredNodes);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName(emitStableDatabaseSemconv() ? "get" : "getBulk", target))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? "get" : "getBulk"),
                            equalTo(SERVER_ADDRESS, emitStableDatabaseSemconv() ? target : null),
                            equalTo(NETWORK_PEER_ADDRESS, null),
                            equalTo(NETWORK_PEER_PORT, null))));
  }

  @Test
  void severalConfiguredNodesAreReportedTogether() {
    MemcachedClient memcached = getMemcached(asList(memcachedAddress, secondMemcachedAddress));
    testing.runWithSpan(
        "parent", () -> assertThat(memcached.get(key("test-several-nodes"))).isNull());

    String address =
        memcachedAddress.getHostString()
            + ":"
            + memcachedAddress.getPort()
            + ","
            + secondMemcachedAddress.getHostString()
            + ":"
            + secondMemcachedAddress.getPort();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("get", address))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            satisfies(
                                SERVER_ADDRESS,
                                val -> {
                                  if (emitStableDatabaseSemconv()) {
                                    val.isEqualTo(address);
                                  } else {
                                    val.isIn(
                                        memcachedAddress.getHostString(),
                                        secondMemcachedAddress.getHostString());
                                  }
                                }),
                            satisfies(
                                SERVER_PORT,
                                val -> {
                                  if (emitStableDatabaseSemconv()) {
                                    val.isNull();
                                  } else {
                                    val.isIn(
                                        (long) memcachedAddress.getPort(),
                                        (long) secondMemcachedAddress.getPort());
                                  }
                                }),
                            satisfies(
                                NETWORK_PEER_ADDRESS,
                                val -> {
                                  if (emitStableDatabaseSemconv()) {
                                    val.isIn(
                                        memcachedAddress.getAddress().getHostAddress(),
                                        secondMemcachedAddress.getAddress().getHostAddress());
                                  } else {
                                    val.isNull();
                                  }
                                }),
                            satisfies(
                                NETWORK_PEER_PORT,
                                val -> {
                                  if (emitStableDatabaseSemconv()) {
                                    val.isIn(
                                        (long) memcachedAddress.getPort(),
                                        (long) secondMemcachedAddress.getPort());
                                  } else {
                                    val.isNull();
                                  }
                                }),
                            equalTo(stringKey("spymemcached.result"), experimental("miss")))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.spymemcached-2.12",
        DB_SYSTEM_NAME,
        maybeStable(DB_OPERATION),
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT,
        SERVER_ADDRESS);
  }

  @Test
  void clientKeepsTheNodesItWasCreatedWith() {
    List<InetSocketAddress> nodes = new ArrayList<>();
    nodes.add(memcachedAddress);
    MemcachedClient memcached = getMemcached(nodes);

    nodes.add(secondMemcachedAddress);

    testing.runWithSpan(
        "parent", () -> assertThat(memcached.get(key("test-node-list-mutation"))).isNull());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                emitStableDatabaseSemconv()
                                    ? memcachedAddress.getAddress().getHostAddress()
                                    : null),
                            equalTo(
                                NETWORK_PEER_PORT,
                                emitStableDatabaseSemconv()
                                    ? (long) memcachedAddress.getPort()
                                    : null),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("miss")))));
  }

  private static String key(String k) {
    return KEY_PREFIX + k;
  }

  private static List<String> keysForNode(
      MemcachedConnection connection, MemcachedNode node, int count) {
    List<String> keys = new ArrayList<>();
    for (int i = 0; keys.size() < count; i++) {
      String candidate = key("optimized-retry-" + i);
      if (connection.getLocator().getPrimary(candidate) == node) {
        keys.add(candidate);
      }
    }
    return keys;
  }

  private static void waitForNodes(MemcachedConnection connection) {
    await()
        .untilAsserted(
            () -> assertThat(connection.getLocator().getAll()).allMatch(MemcachedNode::isActive));
  }

  private static MemcachedNode nextNode(
      MemcachedConnection connection, String key, MemcachedNode... excludedNodes) {
    Iterator<MemcachedNode> sequence = connection.getLocator().getSequence(key);
    while (sequence.hasNext()) {
      MemcachedNode candidate = sequence.next();
      boolean excluded = false;
      for (MemcachedNode excludedNode : excludedNodes) {
        if (candidate == excludedNode) {
          excluded = true;
          break;
        }
      }
      if (!excluded && candidate.isActive()) {
        return candidate;
      }
    }
    throw new AssertionError("No available retry node for " + key);
  }

  private static Operation onlyOperation(Collection<Operation> operations) {
    assertThat(operations).hasSize(1);
    return operations.iterator().next();
  }

  private static QueuedOperation onlyQueuedOperation(
      Collection<MemcachedNode> nodes, MemcachedNode... excludedNodes) {
    QueuedOperation result = null;
    for (MemcachedNode node : nodes) {
      boolean excluded = false;
      for (MemcachedNode excludedNode : excludedNodes) {
        if (node == excludedNode) {
          excluded = true;
          break;
        }
      }
      if (!excluded) {
        Collection<Operation> operations = node.destroyInputQueue();
        if (!operations.isEmpty()) {
          assertThat(result).isNull();
          result = new QueuedOperation(node, onlyOperation(operations));
        }
      }
    }
    assertThat(result).isNotNull();
    return result;
  }

  private static void markForRetry(Operation operation) throws ReflectiveOperationException {
    Method transitionState =
        BaseOperationImpl.class.getDeclaredMethod("transitionState", OperationState.class);
    transitionState.setAccessible(true);
    transitionState.invoke(operation, OperationState.RETRY);
  }

  private static InetSocketAddress startAdditionalMemcached() {
    GenericContainer<?> container =
        new GenericContainer<>("memcached:1.6.41")
            .withExposedPorts(11211)
            .withStartupTimeout(Duration.ofMinutes(2));
    container.start();
    cleanup.deferCleanup(container::stop);
    return new InetSocketAddress(container.getHost(), container.getMappedPort(11211));
  }

  private static String spanName(String operation) {
    return spanName(operation, memcachedAddress.getHostString() + ":" + memcachedAddress.getPort());
  }

  private static String spanName(String operation, String target) {
    return emitStableDatabaseSemconv() ? operation + " " + target : operation;
  }

  private static String configuredTarget(List<InetSocketAddress> nodes) {
    StringBuilder target = new StringBuilder();
    for (InetSocketAddress node : nodes) {
      if (target.length() > 0) {
        target.append(',');
      }
      String host = node.getHostString();
      if (host.indexOf(':') >= 0) {
        target.append('[').append(host).append(']');
      } else {
        target.append(host);
      }
      target.append(':').append(node.getPort());
    }
    return target.toString();
  }

  private static <T> T experimental(T value) {
    return EXPERIMENTAL_ATTRIBUTES ? value : null;
  }

  private static String longString() {
    char[] chars = new char[250];
    Arrays.fill(chars, 's');
    return new String(chars);
  }

  private static BlockingQueue<Operation> getLockableQueue(ReentrantLock queueLock) {
    return new ArrayBlockingQueue<Operation>(DefaultConnectionFactory.DEFAULT_OP_QUEUE_LEN) {

      @Override
      public int drainTo(Collection<? super Operation> c, int maxElements) {
        queueLock.lock();
        try {
          return super.drainTo(c, maxElements);
        } finally {
          queueLock.unlock();
        }
      }
    };
  }

  private static class QueuedOperation {
    private final MemcachedNode node;
    private final Operation operation;

    private QueuedOperation(MemcachedNode node, Operation operation) {
      this.node = node;
      this.operation = operation;
    }
  }

  private static class FanoutConnectionFactory extends DefaultConnectionFactory {
    private final ReentrantLock queueLock;

    private FanoutConnectionFactory(ReentrantLock queueLock) {
      this.queueLock = queueLock;
    }

    @Override
    public BlockingQueue<Operation> createOperationQueue() {
      return getLockableQueue(queueLock);
    }

    @Override
    public FailureMode getFailureMode() {
      return Redistribute;
    }

    @Override
    public NodeLocator createLocator(List<MemcachedNode> nodes) {
      return new FanoutNodeLocator(nodes);
    }
  }

  private static class FanoutNodeLocator implements NodeLocator {
    private List<MemcachedNode> nodes;

    private FanoutNodeLocator(List<MemcachedNode> nodes) {
      this.nodes = nodes;
    }

    @Override
    public MemcachedNode getPrimary(String key) {
      return nodes.get(0);
    }

    @Override
    public Iterator<MemcachedNode> getSequence(String key) {
      if (key.endsWith("one")) {
        return asList(nodes.get(1), nodes.get(2)).iterator();
      }
      return asList(nodes.get(2), nodes.get(1)).iterator();
    }

    @Override
    public Collection<MemcachedNode> getAll() {
      return nodes;
    }

    @Override
    public NodeLocator getReadonlyCopy() {
      return new FanoutNodeLocator(new ArrayList<>(nodes));
    }

    @Override
    public void updateLocator(List<MemcachedNode> nodes) {
      this.nodes = nodes;
    }
  }
}
