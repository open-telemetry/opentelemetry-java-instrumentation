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
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.MEMCACHED;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static net.spy.memcached.ConnectionFactoryBuilder.Protocol.BINARY;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.util.concurrent.MoreExecutors;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.ConnectionFactory;
import net.spy.memcached.ConnectionFactoryBuilder;
import net.spy.memcached.DefaultConnectionFactory;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.internal.CheckedOperationTimeoutException;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.ops.Operation;
import net.spy.memcached.ops.OperationQueueFactory;
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
                    span.hasName("get")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(maybeStable(DB_STATEMENT), "get " + key("test-get")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
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
                    span.hasName("get")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "get " + key("test-get-key-that-doesn't-exist")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
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
                    span.hasName("get")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(maybeStable(DB_STATEMENT), "get " + key("test-get")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
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
      GetFuture<Object> future = timingOutMemcached.asyncGet(key("test-get"));
      // While the op is stuck in the locked queue, nothing marks it as timed out on its own;
      // spymemcached only flags an unsent operation as timed out as a side effect of a caller
      // blocking on Future#get(timeout, unit) and that wait expiring. Trigger that here, on a
      // separate thread, so the operation genuinely times out instead of just being delayed
      // until the lock is released (where it would silently succeed).
      Thread timeoutTrigger =
          new Thread(
              () -> {
                try {
                  future.get(TIMING_OUT_OPERATION_TIMEOUT_MILLIS, MILLISECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                } catch (ExecutionException | TimeoutException e) {
                  // expected: this is what flags the operation as timed out
                }
              });
      timeoutTrigger.start();
      Thread.sleep(TIMING_OUT_OPERATION_TIMEOUT_MILLIS + 1000);
      timeoutTrigger.join();
    } finally {
      queueLock.unlock();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("get")
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
                            equalTo(maybeStable(DB_STATEMENT), "get " + key("test-get")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211)))));
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
                    span.hasName("getBulk")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "getBulk"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "getBulk " + key("test-get") + " " + key("test-get-2")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211)))));
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
                    span.hasName("set")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "set"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "set " + key("test-set") + " " + EXPIRATION_SECONDS + " ?"),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211)))));
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
                    span.hasName("set")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "set"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "set " + key("test-set-cancel") + " " + EXPIRATION_SECONDS + " ?"),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
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
                    span.hasName("add")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "add"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "add " + key("test-add") + " " + EXPIRATION_SECONDS + " ?"),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211))),
                span ->
                    span.hasName("get")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(maybeStable(DB_STATEMENT), "get " + key("test-add")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
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
                    span.hasName("add")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "add"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "add " + key("test-add2") + " " + EXPIRATION_SECONDS + " ?"),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211))),
                span ->
                    span.hasName("add")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "add"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "add " + key("test-add2") + " " + EXPIRATION_SECONDS + " ?"),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211)))));
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
                    span.hasName("delete")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "delete"),
                            equalTo(maybeStable(DB_STATEMENT), "delete " + key("test-delete")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211))),
                span ->
                    span.hasName("get")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(maybeStable(DB_STATEMENT), "get " + key("test-delete")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
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
                    span.hasName("delete")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "delete"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "delete " + key("test-delete-non-existent")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211)))));
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
                    span.hasName("replace")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "replace"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "replace " + key("test-replace") + " " + EXPIRATION_SECONDS + " ?"),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211))),
                span ->
                    span.hasName("get")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(maybeStable(DB_STATEMENT), "get " + key("test-replace")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
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
                    span.hasName("replace")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "replace"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "replace "
                                    + key("test-replace-non-existent")
                                    + " "
                                    + EXPIRATION_SECONDS
                                    + " ?"),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211)))));
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
                    span.hasName("gets")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "gets"),
                            equalTo(maybeStable(DB_STATEMENT), "gets " + key("test-append")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211))),
                span ->
                    span.hasName("append")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "append"),
                            satisfies(
                                maybeStable(DB_STATEMENT),
                                val ->
                                    val.startsWith("append " + key("test-append") + " ")
                                        .endsWith(" ?")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211))),
                span ->
                    span.hasName("get")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(maybeStable(DB_STATEMENT), "get " + key("test-append")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
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
                    span.hasName("gets")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "gets"),
                            equalTo(maybeStable(DB_STATEMENT), "gets " + key("test-prepend")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211))),
                span ->
                    span.hasName("prepend")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "prepend"),
                            satisfies(
                                maybeStable(DB_STATEMENT),
                                val ->
                                    val.startsWith("prepend " + key("test-prepend") + " ")
                                        .endsWith(" ?")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211))),
                span ->
                    span.hasName("get")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(maybeStable(DB_STATEMENT), "get " + key("test-prepend")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
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
                    span.hasName("gets")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "gets"),
                            equalTo(maybeStable(DB_STATEMENT), "gets " + key("test-cas")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211))),
                span ->
                    span.hasName("cas")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "cas"),
                            satisfies(
                                maybeStable(DB_STATEMENT),
                                val ->
                                    val.startsWith("cas " + key("test-cas") + " ")
                                        .endsWith(" " + EXPIRATION_SECONDS + " ?")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211)))));
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
                    span.hasName("cas")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "cas"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "cas "
                                    + key("test-cas-doesnt-exist")
                                    + " 1234 "
                                    + EXPIRATION_SECONDS
                                    + " ?"),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211)))));
  }

  @Test
  void touch() throws Exception {
    MemcachedClient memcached = getMemcached(singletonMap("test-touch", "touch test"));
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.touch(key("test-touch"), EXPIRATION_SECONDS).get()).isTrue();
          assertThat(memcached.get(key("test-touch"))).isEqualTo("touch test");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName("touch")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "touch"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "touch " + key("test-touch") + " " + EXPIRATION_SECONDS),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211))),
                span ->
                    span.hasName("get")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(maybeStable(DB_STATEMENT), "get " + key("test-touch")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void incr() {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.incr(key("test-incr"), 5, 10, EXPIRATION_SECONDS)).isEqualTo(10);
          assertThat(memcached.incr(key("test-incr"), 5, 10, EXPIRATION_SECONDS)).isEqualTo(15);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName("incr")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "incr"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "incr " + key("test-incr") + " 5 10 " + EXPIRATION_SECONDS),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211))),
                span ->
                    span.hasName("incr")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "incr"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "incr " + key("test-incr") + " 5 10 " + EXPIRATION_SECONDS),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211)))));
  }

  @Test
  void decr() {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.decr(key("test-decr"), 5, 10, EXPIRATION_SECONDS)).isEqualTo(10);
          assertThat(memcached.decr(key("test-decr"), 5, 10, EXPIRATION_SECONDS)).isEqualTo(5);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName("decr")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "decr"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "decr " + key("test-decr") + " 5 10 " + EXPIRATION_SECONDS),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211))),
                span ->
                    span.hasName("decr")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "decr"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "decr " + key("test-decr") + " 5 10 " + EXPIRATION_SECONDS),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211)))));
  }

  @Test
  void asyncIncrDecr() throws Exception {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.asyncIncr(key("test-async-incr"), 5).get()).isEqualTo(-1L);
          assertThat(memcached.asyncDecr(key("test-async-decr"), 5).get()).isEqualTo(-1L);
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName("incr")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "incr"),
                            equalTo(
                                maybeStable(DB_STATEMENT), "incr " + key("test-async-incr") + " 5"),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211))),
                span ->
                    span.hasName("decr")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "decr"),
                            equalTo(
                                maybeStable(DB_STATEMENT), "decr " + key("test-async-decr") + " 5"),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211)))));
  }

  @Test
  void flush() throws Exception {
    MemcachedClient memcached = getMemcached(singletonMap("test-flush", "flush test"));
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.flush().get()).isTrue();
          assertThat(memcached.get(key("test-flush"))).isNull();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName("get")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "get"),
                            equalTo(maybeStable(DB_STATEMENT), "get " + key("test-flush")),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("miss")))));
  }

  @Test
  void testSanitizationDisabled() throws Exception {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.set(key("test-sanitization"), EXPIRATION_SECONDS, "secret").get())
              .isTrue();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName("set")
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), MEMCACHED),
                            equalTo(maybeStable(DB_OPERATION), "set"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "set "
                                    + key("test-sanitization")
                                    + " "
                                    + EXPIRATION_SECONDS
                                    + " secret"),
                            equalTo(SERVER_ADDRESS, memcachedContainer.getHost()),
                            equalTo(SERVER_PORT, memcachedContainer.getMappedPort(11211)))));
  }

  private static String key(String k) {
    return KEY_PREFIX + k;
  }

  private static <T> T experimental(T value) {
    return EXPERIMENTAL_ATTRIBUTES ? value : null;
  }

  private static BlockingQueue<Operation> getLockableQueue(ReentrantLock queueLock) {
    return new ArrayBlockingQueue<Operation>(DefaultConnectionFactory.DEFAULT_OP_QUEUE_LEN) {
      @Override
      public boolean offer(Operation o) {
        queueLock.lock();
        try {
          return super.offer(o);
        } finally {
          queueLock.unlock();
        }
      }
    };
  }
}
