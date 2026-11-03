/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spymemcached.v2_12;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
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
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.javaagent.testing.common.TestAgentListenerAccess;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.spy.memcached.CASResponse;
import net.spy.memcached.CASValue;
import net.spy.memcached.CachedData;
import net.spy.memcached.ConnectionFactory;
import net.spy.memcached.ConnectionFactoryBuilder;
import net.spy.memcached.ConnectionFactoryBuilder.Protocol;
import net.spy.memcached.DefaultConnectionFactory;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.MemcachedConnection;
import net.spy.memcached.MemcachedNode;
import net.spy.memcached.internal.CheckedOperationTimeoutException;
import net.spy.memcached.internal.GetFuture;
import net.spy.memcached.internal.OperationFuture;
import net.spy.memcached.ops.KeyedOperation;
import net.spy.memcached.ops.Operation;
import net.spy.memcached.ops.OperationException;
import net.spy.memcached.ops.OperationQueueFactory;
import net.spy.memcached.ops.OperationState;
import net.spy.memcached.protocol.BaseOperationImpl;
import net.spy.memcached.protocol.ascii.AsciiMemcachedNodeImpl;
import net.spy.memcached.transcoders.SerializingTranscoder;
import net.spy.memcached.transcoders.Transcoder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.GenericContainer;

@SuppressWarnings("deprecation") // using deprecated semconv
class SpymemcachedTest {
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
              if (!memcached.set(entry.getKey(), EXPIRATION_SECONDS, entry.getValue()).get()) {
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
        "parent", () -> assertThat(memcached.get("test-get")).isEqualTo("get test"));

    assertDurationMetric(
        testing,
        "io.opentelemetry.spymemcached-2.12",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @Test
  void storeRejectedAsTooLarge() throws Exception {
    MemcachedClient memcached = getMemcached();
    byte[] value = new byte[2 * 1024 * 1024];
    new Random(0).nextBytes(value);
    String errorType = testLatestDeps() ? "ERR_2BIG" : OperationException.class.getName();

    testing.runWithSpan(
        "parent",
        () -> {
          OperationFuture<Boolean> future = memcached.set("too-large", EXPIRATION_SECONDS, value);
          if (testLatestDeps()) {
            assertThat(future.get()).isFalse();
            assertThat(future.getStatus().getStatusCode().name()).isEqualTo("ERR_2BIG");
          } else {
            assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(OperationException.class);
          }
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent"),
                span ->
                    span.hasName(spanName("set"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "set"),
                            equalTo(DB_QUERY_TEXT, "set too-large 3600 ?"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, memcachedAddress.getPort()),
                            equalTo(ERROR_TYPE, errorType))));
    assertDurationMetric(
        testing,
        "io.opentelemetry.spymemcached-2.12",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT,
        SERVER_ADDRESS,
        SERVER_PORT,
        ERROR_TYPE);
  }

  @Test
  void getHit() {
    MemcachedClient memcached = getMemcached(singletonMap("test-get", "get test"));
    testing.runWithSpan(
        "parent", () -> assertThat(memcached.get("test-get")).isEqualTo("get test"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get test-get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void getMiss() {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent", () -> assertThat(memcached.get("test-get-key-that-doesn't-exist")).isNull());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get test-get-key-that-doesn't-exist"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
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
            lockableMemcached.asyncGet("test-get").cancel(true);
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get test-get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
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
      timingOutMemcached.asyncGet("test-get");
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
                                "net.spy.memcached.internal.CheckedOperationTimeoutException"),
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get test-get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @ParameterizedTest
  @CsvSource({
    "varargs,false,true",
    "collection,false,true",
    "transcoder-varargs,false,true",
    "iterator,false,false",
    "varargs,true,true",
    "collection,true,true",
    "transcoder-varargs,true,true",
    "iterator,true,false"
  })
  void bulkGet(String overload, boolean async, boolean capturesKeys) throws Exception {
    Context original = Context.current();
    Map<String, String> testData = new HashMap<>();
    testData.put("test-get", "get test");
    testData.put("test-get-2", "get test 2");
    MemcachedClient memcached = getMemcached(testData);
    List<String> keys = asList("test-get", "test-get-2");
    Map<String, Object> result =
        testing.runWithSpan(
            "parent",
            () -> {
              switch (overload) {
                case "collection":
                  return async ? memcached.asyncGetBulk(keys).get() : memcached.getBulk(keys);
                case "transcoder-varargs":
                  return async
                      ? memcached
                          .asyncGetBulk(memcached.getTranscoder(), keys.toArray(new String[0]))
                          .get()
                      : memcached.getBulk(memcached.getTranscoder(), keys.toArray(new String[0]));
                case "iterator":
                  return async
                      ? memcached.asyncGetBulk(keys.iterator()).get()
                      : memcached.getBulk(keys.iterator());
                default:
                  return async
                      ? memcached.asyncGetBulk(keys.toArray(new String[0])).get()
                      : memcached.getBulk(keys.toArray(new String[0]));
              }
            });
    assertThat(Context.current()).isSameAs(original);
    assertThat(result)
        .hasSize(2)
        .containsEntry("test-get", "get test")
        .containsEntry("test-get-2", "get test 2");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(
                                DB_QUERY_TEXT,
                                capturesKeys ? "getBulk test-get test-get-2" : "getBulk"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void bulkGetWithIteratorTranscodersOmitsKeysWhenCountsDiffer() throws Exception {
    MemcachedClient memcached = getMemcached(singletonMap("test-get", "get test"));
    List<String> keys = asList("test-get", "test-get-2");
    Iterator<Transcoder<Object>> transcoders = singletonList(memcached.getTranscoder()).iterator();

    Map<String, Object> result =
        testing.runWithSpan("parent", () -> memcached.asyncGetBulk(keys, transcoders).get());

    assertThat(result).hasSize(1).containsEntry("test-get", "get test");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("get"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "getBulk"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void failedBulkCaptureDoesNotSuppressSubsequentRequest() throws Exception {
    MemcachedClient memcached = getMemcached(singletonMap("test-get", "get test"));
    AtomicBoolean failFirstIteration = new AtomicBoolean(true);
    Collection<String> keys =
        new AbstractCollection<String>() {
          @Override
          public Iterator<String> iterator() {
            if (failFirstIteration.getAndSet(false)) {
              throw new IllegalStateException("query capture failed");
            }
            return singletonList("test-get").iterator();
          }

          @Override
          public int size() {
            return 1;
          }
        };

    testing.runWithSpan(
        "parent",
        () -> {
          Map<String, Object> bulkResult = memcached.asyncGetBulk(keys).get();
          assertThat(bulkResult).hasSize(1).containsEntry("test-get", "get test");
          assertThat(TestAgentListenerAccess.getAndResetAdviceFailureCount()).isEqualTo(1);
          assertThat(memcached.asyncGet("test-get").get()).isEqualTo("get test");
          return null;
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "getBulk test-get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get test-get"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void reentrantBulkRequestUsesItsOwnQueryText() throws Exception {
    String outerKey = "outer";
    String nestedKey = "nested";
    MemcachedClient memcached = getMemcached(singletonMap("outer", "outer value"));
    SerializingTranscoder transcoder =
        new SerializingTranscoder() {
          @Override
          public Object decode(CachedData cachedData) {
            memcached.asyncGetBulk(singletonList(nestedKey));
            return super.decode(cachedData);
          }
        };

    testing.runWithSpan(
        "parent",
        () ->
            assertThat(memcached.getBulk(singletonList(outerKey), transcoder))
                .containsEntry(outerKey, "outer value"));

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("get"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "getBulk " + outerKey),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(spanName("get"))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "getBulk " + nestedKey),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void bulkGetCancellationPreservesQueryAndDoesNotReportError() {
    ReentrantLock queueLock = new ReentrantLock();
    MemcachedClient memcached =
        getMemcached(
            emptyMap(),
            builder ->
                builder.setOpQueueFactory(
                    () -> getLockableQueue(queueLock, (queue, operation) -> operation.cancel())));
    Context original = Context.current();
    queueLock.lock();
    try {
      testing.runWithSpan(
          "parent",
          () ->
              assertThatThrownBy(() -> memcached.getBulk("cancel-bulk"))
                  .isInstanceOf(CancellationException.class));
    } finally {
      queueLock.unlock();
    }
    assertThat(Context.current()).isSameAs(original);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasStatus(StatusData.unset())
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "getBulk cancel-bulk"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, memcachedAddress.getPort()),
                            equalTo(
                                booleanKey("spymemcached.command.cancelled"),
                                experimental(true)))));
  }

  @Test
  void set() throws Exception {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.set("test-set", EXPIRATION_SECONDS, "bar").get()).isTrue();
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "set"),
                            equalTo(DB_QUERY_TEXT, "set test-set 3600 ?"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
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
            assertThat(lockableMemcached.set("test-set-cancel", EXPIRATION_SECONDS, "bar").cancel())
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "set"),
                            equalTo(DB_QUERY_TEXT, "set test-set-cancel 3600 ?"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
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
          assertThat(memcached.add("test-add", EXPIRATION_SECONDS, "add bar").get()).isTrue();
          assertThat(memcached.get("test-add")).isEqualTo("add bar");
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "add"),
                            equalTo(DB_QUERY_TEXT, "add test-add 3600 ?"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get test-add"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void secondAdd() throws Exception {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.add("test-add2", EXPIRATION_SECONDS, "add bar").get()).isTrue();
          assertThat(memcached.add("test-add2", EXPIRATION_SECONDS, "add bar 123").get()).isFalse();
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "add"),
                            equalTo(DB_QUERY_TEXT, "add test-add2 3600 ?"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("add"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "add"),
                            equalTo(DB_QUERY_TEXT, "add test-add2 3600 ?"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void delete() throws Exception {
    MemcachedClient memcached = getMemcached(singletonMap("test-delete", "delete test"));
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.delete("test-delete").get()).isTrue();
          assertThat(memcached.get("test-delete")).isNull();
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "delete"),
                            equalTo(DB_QUERY_TEXT, "delete test-delete"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get test-delete"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("miss")))));
  }

  @Test
  void deleteNonExistent() throws Exception {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.delete("test-delete-non-existent").get()).isFalse();
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "delete"),
                            equalTo(DB_QUERY_TEXT, "delete test-delete-non-existent"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void replace() throws Exception {
    MemcachedClient memcached = getMemcached(singletonMap("test-replace", "replace test"));
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.replace("test-replace", EXPIRATION_SECONDS, "new value").get())
              .isTrue();
          assertThat(memcached.get("test-replace")).isEqualTo("new value");
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "replace"),
                            equalTo(DB_QUERY_TEXT, "replace test-replace 3600 ?"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get test-replace"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
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
                      .replace("test-replace-non-existent", EXPIRATION_SECONDS, "new value")
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "replace"),
                            equalTo(DB_QUERY_TEXT, "replace test-replace-non-existent 3600 ?"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void append() throws Exception {
    MemcachedClient memcached = getMemcached(singletonMap("test-append", "append test"));
    testing.runWithSpan(
        "parent",
        () -> {
          CASValue<Object> casValue = memcached.gets("test-append");
          assertThat(memcached.append(casValue.getCas(), "test-append", " appended").get())
              .isTrue();
          assertThat(memcached.get("test-append")).isEqualTo("append test appended");
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "gets"),
                            equalTo(DB_QUERY_TEXT, "gets test-append"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("append"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "append"),
                            satisfies(
                                DB_QUERY_TEXT, val -> val.matches("append \\d+ test-append \\?")),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get test-append"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void prepend() throws Exception {
    MemcachedClient memcached = getMemcached(singletonMap("test-prepend", "prepend test"));
    testing.runWithSpan(
        "parent",
        () -> {
          CASValue<Object> casValue = memcached.gets("test-prepend");
          assertThat(memcached.prepend(casValue.getCas(), "test-prepend", "prepended ").get())
              .isTrue();
          assertThat(memcached.get("test-prepend")).isEqualTo("prepended prepend test");
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "gets"),
                            equalTo(DB_QUERY_TEXT, "gets test-prepend"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("prepend"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "prepend"),
                            satisfies(
                                DB_QUERY_TEXT, val -> val.matches("prepend \\d+ test-prepend \\?")),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get test-prepend"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void cas() {
    MemcachedClient memcached = getMemcached(singletonMap("test-cas", "cas test"));
    testing.runWithSpan(
        "parent",
        () -> {
          CASValue<Object> casValue = memcached.gets("test-cas");
          assertThat(memcached.cas("test-cas", casValue.getCas(), EXPIRATION_SECONDS, "cas bar"))
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "gets"),
                            equalTo(DB_QUERY_TEXT, "gets test-cas"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("cas"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "cas"),
                            satisfies(
                                DB_QUERY_TEXT, val -> val.matches("cas test-cas \\d+ 3600 \\?")),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @ParameterizedTest
  @CsvSource({"false,false", "true,false", "true,true"})
  void casNotFound(boolean numericValue, boolean withExpiration) throws Exception {
    MemcachedClient memcached = getMemcached();
    String casKey = "test-cas-doesnt-exist";
    CASResponse result =
        testing.runWithSpan(
            "parent",
            () -> {
              if (!numericValue) {
                return memcached.cas(casKey, 1234, EXPIRATION_SECONDS, "cas bar");
              }
              if (withExpiration) {
                return memcached
                    .asyncCAS(casKey, 1234, EXPIRATION_SECONDS, 5678, memcached.getTranscoder())
                    .get();
              }
              return memcached
                  .asyncCAS(casKey, 1234, (Object) 5678, memcached.getTranscoder())
                  .get();
            });
    assertThat(result).isEqualTo(CASResponse.NOT_FOUND);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("cas"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "cas"),
                            equalTo(
                                DB_QUERY_TEXT,
                                "cas "
                                    + casKey
                                    + (numericValue && !withExpiration
                                        ? " 1234 ?"
                                        : " 1234 3600 ?")),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void touch() throws Exception {
    MemcachedClient memcached = getMemcached(singletonMap("test-touch", "touch test"));
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.touch("test-touch", EXPIRATION_SECONDS).get()).isTrue();
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "touch"),
                            equalTo(DB_QUERY_TEXT, "touch test-touch 3600"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void touchNonExistent() throws Exception {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.touch("test-touch-non-existent", EXPIRATION_SECONDS).get())
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "touch"),
                            equalTo(DB_QUERY_TEXT, "touch test-touch-non-existent 3600"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void getAndTouch() {
    MemcachedClient memcached = getMemcached(singletonMap("test-touch", "touch test"));
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.getAndTouch("test-touch", EXPIRATION_SECONDS).getValue())
              .isEqualTo("touch test");
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("gat"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "gat"),
                            equalTo(DB_QUERY_TEXT, "getAndTouch test-touch 3600"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void getAndTouchNonExistent() {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.getAndTouch("test-touch-non-existent", EXPIRATION_SECONDS)).isNull();
        });

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("gat"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "gat"),
                            equalTo(DB_QUERY_TEXT, "getAndTouch test-touch-non-existent 3600"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
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
          assertThat(memcached.decr("test-decr", 5)).isEqualTo(195);
          assertThat(memcached.get("test-decr")).isEqualTo("195");
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "decr"),
                            equalTo(DB_QUERY_TEXT, "decr test-decr 5"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get test-decr"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void decrNonExistent() {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.decr("test-decr-non-existent", 5)).isEqualTo(-1);
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "decr"),
                            equalTo(DB_QUERY_TEXT, "decr test-decr-non-existent 5"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void decrException() {
    MemcachedClient memcached = getMemcached();
    assertThatThrownBy(() -> memcached.decr("long key: " + longString(), 5))
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
                            equalTo(ERROR_TYPE, "java.lang.IllegalArgumentException"),
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "decr"),
                            equalTo(DB_QUERY_TEXT, "decr long key: " + longString() + " 5"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(SERVER_PORT, (long) memcachedAddress.getPort()))));
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
          assertThat(memcached.incr("test-incr", 5)).isEqualTo(105);
          assertThat(memcached.get("test-incr")).isEqualTo("105");
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "incr"),
                            equalTo(DB_QUERY_TEXT, "incr test-incr 5"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort())),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get test-incr"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("hit")))));
  }

  @Test
  void incrNonExistent() {
    MemcachedClient memcached = getMemcached();
    testing.runWithSpan(
        "parent",
        () -> {
          assertThat(memcached.incr("test-incr-non-existent", 5)).isEqualTo(-1);
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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "incr"),
                            equalTo(DB_QUERY_TEXT, "incr test-incr-non-existent 5"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()))));
  }

  @Test
  void incrException() {
    MemcachedClient memcached = getMemcached();
    assertThatThrownBy(() -> memcached.incr("long key: " + longString(), 5))
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
                            equalTo(ERROR_TYPE, "java.lang.IllegalArgumentException"),
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "incr"),
                            equalTo(DB_QUERY_TEXT, "incr long key: " + longString() + " 5"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(SERVER_PORT, (long) memcachedAddress.getPort()))));
  }

  @Test
  void optimizedRedistributionOmitsPeerFromEveryRequest() throws Exception {
    ReentrantLock queueLock = new ReentrantLock();
    OperationQueueFactory lockableQueueFactory = () -> getLockableQueue(queueLock);
    MemcachedClient memcached =
        getMemcached(
            asList(memcachedAddress, secondMemcachedAddress),
            builder ->
                builder
                    .setProtocol(TEXT)
                    .setFailureMode(Redistribute)
                    .setOpQueueFactory(lockableQueueFactory));
    MemcachedConnection connection = memcached.getConnection();
    waitForNodes(connection);
    MemcachedNode failedNode = connection.getLocator().getPrimary("optimized-retry-0");
    List<String> keys = keysForNode(connection, failedNode, 2);

    List<GetFuture<Object>> futures;
    Operation optimizedOperation;
    queueLock.lock();
    try {
      futures =
          testing.runWithSpan(
              "parent",
              () -> asList(memcached.asyncGet(keys.get(0)), memcached.asyncGet(keys.get(1))));
      Collection<Operation> operations = failedNode.destroyInputQueue();
      assertThat(operations).hasSize(2);
      ConnectionFactory optimizerFactory = new ConnectionFactoryBuilder().setProtocol(TEXT).build();
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

    String target =
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
                    span.hasName(spanName("get", target))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get " + keys.get(0)),
                            equalTo(SERVER_ADDRESS, target),
                            equalTo(stringKey("spymemcached.result"), experimental("miss"))),
                span ->
                    span.hasName(spanName("get", target))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get " + keys.get(1)),
                            equalTo(SERVER_ADDRESS, target),
                            equalTo(stringKey("spymemcached.result"), experimental("miss")))));
  }

  @ParameterizedTest
  @CsvSource({"TEXT,1", "TEXT,2", "BINARY,1", "BINARY,2"})
  void sequentialSingleKeyRetriesUseLastPeer(Protocol protocol, int retries) throws Exception {
    List<InetSocketAddress> configuredNodes = asList(memcachedAddress, secondMemcachedAddress);
    ReentrantLock queueLock = new ReentrantLock();
    OperationQueueFactory lockableQueueFactory = () -> getLockableQueue(queueLock);
    MemcachedClient memcached =
        getMemcached(
            configuredNodes,
            builder ->
                builder
                    .setProtocol(protocol)
                    .setFailureMode(Redistribute)
                    .setOpQueueFactory(lockableQueueFactory));
    MemcachedConnection connection = memcached.getConnection();
    waitForNodes(connection);
    String requestKey = "redistribution";
    MemcachedNode initialNode = connection.getLocator().getPrimary(requestKey);
    MemcachedNode retryNode =
        connection.getLocator().getAll().stream()
            .filter(node -> node != initialNode)
            .findFirst()
            .get();

    GetFuture<Object> future;
    queueLock.lock();
    try {
      future = testing.runWithSpan("parent", () -> memcached.asyncGet(requestKey));
      Operation initialOperation = onlyOperation(initialNode.destroyInputQueue());
      markForRetry(initialOperation);
      initialNode.reconnecting();
      connection.redistributeOperation(initialOperation);
      if (retries == 2) {
        Operation retryOperation = onlyOperation(retryNode.destroyInputQueue());
        assertThat(retryOperation).isNotSameAs(initialOperation);
        markForRetry(retryOperation);
        initialNode.connected();
        retryNode.reconnecting();
        connection.redistributeOperation(retryOperation);
      }
    } finally {
      queueLock.unlock();
    }

    assertThat(future.get()).isNull();

    InetSocketAddress peer =
        (InetSocketAddress) (retries == 1 ? retryNode : initialNode).getSocketAddress();
    String target =
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
                    span.hasName(spanName("get", target))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get " + requestKey),
                            equalTo(SERVER_ADDRESS, target),
                            equalTo(NETWORK_PEER_ADDRESS, peer.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) peer.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("miss")))));
  }

  @Test
  void retryBeforeInitialEnqueueReturnsKeepsRetryPeer() throws Exception {
    ReentrantLock queueLock = new ReentrantLock();
    AtomicReference<BiConsumer<BlockingQueue<Operation>, Operation>> afterEnqueue =
        new AtomicReference<>();
    ExecutorService ioThread = Executors.newSingleThreadExecutor();
    cleanup.deferCleanup(ioThread::shutdownNow);
    MemcachedClient memcached =
        getMemcached(
            asList(memcachedAddress, secondMemcachedAddress),
            builder ->
                builder
                    .setProtocol(TEXT)
                    .setFailureMode(Redistribute)
                    .setOpQueueFactory(
                        () ->
                            getLockableQueue(
                                queueLock,
                                (queue, operation) -> {
                                  BiConsumer<BlockingQueue<Operation>, Operation> action =
                                      afterEnqueue.getAndSet(null);
                                  if (action != null) {
                                    action.accept(queue, operation);
                                  }
                                })));
    MemcachedConnection connection = memcached.getConnection();
    waitForNodes(connection);
    String requestKey = "early-retry";
    MemcachedNode initialNode = connection.getLocator().getPrimary(requestKey);
    InetSocketAddress retryPeer =
        (InetSocketAddress)
            connection.getLocator().getAll().stream()
                .filter(node -> node != initialNode)
                .findFirst()
                .get()
                .getSocketAddress();

    GetFuture<Object> future;
    queueLock.lock();
    try {
      afterEnqueue.set(
          (queue, operation) -> {
            assertThat(queue.remove(operation)).isTrue();
            assertThat(
                    ioThread.submit(
                        () -> {
                          markForRetry(operation);
                          initialNode.reconnecting();
                          connection.redistributeOperation(operation);
                          return null;
                        }))
                .succeedsWithin(Duration.ofSeconds(10));
          });
      future = testing.runWithSpan("parent", () -> memcached.asyncGet(requestKey));
    } finally {
      queueLock.unlock();
    }

    assertThat(future.get()).isNull();
    String target =
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
                    span.hasName(spanName("get", target))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get " + requestKey),
                            equalTo(SERVER_ADDRESS, target),
                            equalTo(NETWORK_PEER_ADDRESS, retryPeer.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) retryPeer.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("miss")))));
  }

  @Test
  void severalConfiguredNodesAreReportedTogether() {
    MemcachedClient memcached = getMemcached(asList(memcachedAddress, secondMemcachedAddress));
    testing.runWithSpan("parent", () -> assertThat(memcached.get("test-several-nodes")).isNull());

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
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get test-several-nodes"),
                            satisfies(
                                SERVER_ADDRESS,
                                val -> {
                                  val.isEqualTo(address);
                                }),
                            satisfies(
                                SERVER_PORT,
                                val -> {
                                  val.isNull();
                                }),
                            satisfies(
                                NETWORK_PEER_ADDRESS,
                                val -> {
                                  val.isIn(
                                      memcachedAddress.getAddress().getHostAddress(),
                                      secondMemcachedAddress.getAddress().getHostAddress());
                                }),
                            satisfies(
                                NETWORK_PEER_PORT,
                                val -> {
                                  val.isIn(
                                      (long) memcachedAddress.getPort(),
                                      (long) secondMemcachedAddress.getPort());
                                }),
                            equalTo(stringKey("spymemcached.result"), experimental("miss")))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.spymemcached-2.12",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
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
        "parent", () -> assertThat(memcached.get("test-node-list-mutation")).isNull());

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasTotalAttributeCount(0),
                span ->
                    span.hasName(spanName("get"))
                        .hasKind(SpanKind.CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, MEMCACHED),
                            equalTo(DB_OPERATION_NAME, "get"),
                            equalTo(DB_QUERY_TEXT, "get test-node-list-mutation"),
                            equalTo(SERVER_ADDRESS, memcachedAddress.getHostString()),
                            equalTo(
                                NETWORK_PEER_ADDRESS,
                                memcachedAddress.getAddress().getHostAddress()),
                            equalTo(NETWORK_PEER_PORT, (long) memcachedAddress.getPort()),
                            equalTo(SERVER_PORT, memcachedAddress.getPort()),
                            equalTo(stringKey("spymemcached.result"), experimental("miss")))));
  }

  private static List<String> keysForNode(
      MemcachedConnection connection, MemcachedNode node, int count) {
    List<String> keys = new ArrayList<>();
    for (int i = 0; keys.size() < count; i++) {
      String candidate = "optimized-retry-" + i;
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

  private static Operation onlyOperation(Collection<Operation> operations) {
    assertThat(operations).hasSize(1);
    return operations.iterator().next();
  }

  private static void markForRetry(Operation operation) throws ReflectiveOperationException {
    Method transitionState =
        BaseOperationImpl.class.getDeclaredMethod("transitionState", OperationState.class);
    transitionState.setAccessible(true);
    transitionState.invoke(operation, OperationState.RETRY);
  }

  private static String spanName(String operation) {
    return spanName(operation, memcachedAddress.getHostString() + ":" + memcachedAddress.getPort());
  }

  private static String spanName(String operation, String target) {
    return operation + " " + target;
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
    return getLockableQueue(queueLock, (queue, operation) -> {});
  }

  private static BlockingQueue<Operation> getLockableQueue(
      ReentrantLock queueLock, BiConsumer<BlockingQueue<Operation>, Operation> afterEnqueue) {
    return new ArrayBlockingQueue<Operation>(DefaultConnectionFactory.DEFAULT_OP_QUEUE_LEN) {

      @Override
      public boolean offer(Operation operation, long timeout, TimeUnit unit)
          throws InterruptedException {
        boolean added = super.offer(operation, timeout, unit);
        if (added) {
          afterEnqueue.accept(this, operation);
        }
        return added;
      }

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
}
