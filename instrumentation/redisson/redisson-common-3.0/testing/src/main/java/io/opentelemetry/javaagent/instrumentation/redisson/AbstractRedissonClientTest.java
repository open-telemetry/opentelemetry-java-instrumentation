/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NetworkTypeValues.IPV4;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Collections.nCopies;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.Redisson;
import org.redisson.api.BatchOptions;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBatch;
import org.redisson.api.RBucket;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;

@SuppressWarnings("deprecation") // using deprecated semconv
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractRedissonClientTest {

  private static final Logger logger = LoggerFactory.getLogger(AbstractRedissonClientTest.class);

  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final String TEST_RECONNECT = "testReconnect";
  private static final String TEST_SINGLE_CONNECTION = "testSingleConnection";

  private final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private String host;
  private String ip;
  private Long port;
  private String address;
  private RedissonClient redisson;

  @BeforeAll
  void setupAll() throws UnknownHostException {
    redisServer.start();
    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379).longValue();
    address = host + ":" + port;
  }

  @AfterAll
  void cleanupAll() {
    redisServer.stop();
  }

  @BeforeEach
  void setup(TestInfo testInfo) throws InvocationTargetException, IllegalAccessException {
    String newAddress = address;
    if (useRedisProtocol()) {
      // Newer versions of redisson require scheme, older versions forbid it
      newAddress = "redis://" + address;
    }
    Config config = new Config();
    try {
      // script cache is enabled by default in 3.46.0 and that causes hashCommand and lockCommand
      // tests to fail
      Config.class.getMethod("setUseScriptCache", boolean.class).invoke(config, false);
    } catch (NoSuchMethodException ignored) {
      // ignored
    }
    SingleServerConfig singleServerConfig = config.useSingleServer();
    singleServerConfig.setAddress(newAddress);
    singleServerConfig.setTimeout(30_000);
    if (testInfo.getTags().contains(TEST_RECONNECT)) {
      // When verifying the stringCommandLazyConnection test case, simulate reconnection during
      // Redis
      // command execution.
      singleServerConfig.setConnectionMinimumIdleSize(0);
    }
    if (testInfo.getTags().contains(TEST_SINGLE_CONNECTION)) {
      singleServerConfig.setConnectionMinimumIdleSize(1);
      singleServerConfig.setConnectionPoolSize(1);
    }
    try {
      // disable connection ping if it exists
      Method method =
          singleServerConfig.getClass().getMethod("setPingConnectionInterval", int.class);
      method.setAccessible(true);
      method.invoke(singleServerConfig, 0);
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
  @Tag(TEST_RECONNECT)
  void stringCommandLazyConnection() {
    testing.runWithSpan(
        "parent",
        () -> {
          RBucket<String> keyObject = redisson.getBucket("foo");
          keyObject.set("bar");
          keyObject.get();
        });
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + address : "SET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET")),
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + address : "GET")
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "GET foo"),
                            equalTo(maybeStable(DB_OPERATION), "GET"))));
  }

  @Test
  void testDurationMetric() {
    AtomicReference<String> instrumentationName = new AtomicReference<>();
    RBucket<String> keyObject = redisson.getBucket("foo");
    keyObject.set("bar");
    testing.waitAndAssertTraces(
        trace -> {
          instrumentationName.set(trace.getSpan(0).getInstrumentationScopeInfo().getName());
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName(emitStableDatabaseSemconv() ? "SET " + address : "SET")
                      .hasKind(CLIENT)
                      .hasAttributesSatisfyingExactly(
                          equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                          equalTo(NETWORK_PEER_ADDRESS, ip),
                          equalTo(NETWORK_PEER_PORT, port),
                          equalTo(SERVER_ADDRESS, host),
                          equalTo(SERVER_PORT, port),
                          equalTo(maybeStable(DB_SYSTEM), REDIS),
                          equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                          equalTo(maybeStable(DB_OPERATION), "SET")));
        });

    assertDurationMetric(
        testing,
        instrumentationName.get(),
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        NETWORK_PEER_PORT,
        NETWORK_PEER_ADDRESS,
        SERVER_PORT,
        SERVER_ADDRESS);
  }

  @Test
  void stringCommand() {
    RBucket<String> keyObject = redisson.getBucket("foo");
    keyObject.set("bar");
    keyObject.get();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanName(
            emitStableDatabaseSemconv() ? "SET " + address : "SET",
            emitStableDatabaseSemconv() ? "GET " + address : "GET"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + address : "SET")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SET foo ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + address : "GET")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "GET foo"),
                            equalTo(maybeStable(DB_OPERATION), "GET"))));
  }

  @ParameterizedTest
  @MethodSource("batchScenarios")
  void batchCommand(BatchScenario scenario) throws ReflectiveOperationException {
    RBatch batch = createBatch(redisson);
    assertThat(batch).isNotNull();
    scenario.commands.forEach(addCommand -> addCommand.accept(batch));
    // Adapt different method signature:
    // `BatchResult<?> execute()` and `List<?> execute()`
    invokeExecute(batch);

    if (scenario.empty) {
      // An empty batch fails before Redisson sends a Redis command, so there is no database client
      // request to report.
      assertThat(testing.spans()).isEmpty();
      return;
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? scenario.operationName + " " + address
                                : scenario.oldSpanName)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv()
                                    ? scenario.operationName
                                    : scenario.oldOperation),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? scenario.batchSize : null),
                            equalTo(maybeStable(DB_STATEMENT), scenario.queryText))));
  }

  private static Stream<Arguments> batchScenarios() {
    return Stream.of(
        // An empty batch fails to execute before any Redis command is sent.
        argumentSet("empty", BatchScenario.builder().empty().build()),
        argumentSet(
            "single",
            BatchScenario.builder()
                .addCommand(batch -> batch.getBucket("batch1").setAsync("v1"))
                .operationName("SET")
                .oldSpanName("SET")
                .oldOperation("SET")
                .queryText("SET batch1 ?")
                .build()),
        argumentSet(
            "twoSameOperation",
            BatchScenario.builder()
                .addCommand(batch -> batch.getBucket("batch1").setAsync("v1"))
                .addCommand(batch -> batch.getBucket("batch2").setAsync("v2"))
                .operationName("PIPELINE SET")
                .oldSpanName("DB Query")
                .batchSize(2)
                .queryText(
                    emitStableDatabaseSemconv()
                        ? "SET batch1 ?; SET batch2 ?"
                        : "SET batch1 ?;SET batch2 ?")
                .build()),
        argumentSet(
            "twoDifferentOperations",
            BatchScenario.builder()
                .addCommand(batch -> batch.getBucket("batch1").setAsync("v1"))
                .addCommand(batch -> batch.getBucket("batch1").getAsync())
                .operationName("PIPELINE")
                .oldSpanName("DB Query")
                .batchSize(2)
                .queryText(
                    emitStableDatabaseSemconv()
                        ? "SET batch1 ?; GET batch1"
                        : "SET batch1 ?;GET batch1")
                .build()));
  }

  private static void invokeExecute(RBatch batch) throws ReflectiveOperationException {
    batch.getClass().getMethod("execute").invoke(batch);
  }

  @Test
  void batchCommandTruncatesQueryText() throws ReflectiveOperationException {
    RBatch batch = createBatch(redisson);
    assertThat(batch).isNotNull();
    StringBuilder bucketNameBuilder = new StringBuilder("bucket");
    for (int i = 0; i < 20_000; i++) {
      bucketNameBuilder.append("a");
    }
    String bucketName = bucketNameBuilder.toString();
    int batchSize = 4;
    int truncatedQueryTextCommandCount = 2;
    for (int i = 0; i < batchSize; i++) {
      batch.getBucket(bucketName).setAsync("v" + i);
    }
    // Adapt different method signature:
    // `BatchResult<?> execute()` and `List<?> execute()`
    invokeExecute(batch);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv() ? "PIPELINE SET " + address : "DB Query")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(
                                DB_OPERATION_NAME,
                                emitStableDatabaseSemconv() ? "PIPELINE SET" : null),
                            equalTo(
                                DB_OPERATION_BATCH_SIZE,
                                emitStableDatabaseSemconv() ? (long) batchSize : null),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                String.join(
                                    emitStableDatabaseSemconv() ? "; " : ";",
                                    nCopies(
                                        truncatedQueryTextCommandCount,
                                        "SET " + bucketName + " ?"))))));
  }

  @Test
  void atomicBatchCommand() {
    try {
      // available since 3.7.2
      Class.forName("org.redisson.api.BatchOptions$ExecutionMode");
    } catch (ClassNotFoundException ignored) {
      Assumptions.abort();
    }

    testing.runWithSpan(
        "parent",
        () -> {
          BatchOptions batchOptions =
              BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.REDIS_WRITE_ATOMIC);
          RBatch batch = redisson.createBatch(batchOptions);
          batch.getBucket("batch1").setAsync("v1");
          batch.getBucket("batch2").setAsync("v2");
          batch.execute();
        });
    if (emitStableDatabaseSemconv()) {
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span -> span.hasName("parent").hasNoParent().hasKind(INTERNAL),
                  span ->
                      span.hasName("MULTI SET")
                          .hasKind(CLIENT)
                          .hasParent(trace.getSpan(0))
                          .hasAttributesSatisfyingExactly(
                              equalTo(DB_SYSTEM_NAME, REDIS),
                              equalTo(DB_OPERATION_NAME, "MULTI SET"),
                              equalTo(DB_OPERATION_BATCH_SIZE, 2L),
                              equalTo(DB_QUERY_TEXT, "SET batch1 ?; SET batch2 ?"),
                              equalTo(DB_SYSTEM, emitOldDatabaseSemconv() ? REDIS : null),
                              equalTo(DB_OPERATION, emitOldDatabaseSemconv() ? "MULTI SET" : null),
                              equalTo(
                                  DB_STATEMENT,
                                  emitOldDatabaseSemconv()
                                      ? "SET batch1 ?; SET batch2 ?"
                                      : null))));
      return;
    }
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasKind(INTERNAL),
                span ->
                    span.hasName("DB Query")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM, REDIS),
                            equalTo(DB_STATEMENT, "MULTI;SET batch1 ?"))
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("SET")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM, REDIS),
                            equalTo(DB_STATEMENT, "SET batch2 ?"),
                            equalTo(DB_OPERATION, "SET"))
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("EXEC")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM, REDIS),
                            equalTo(DB_STATEMENT, "EXEC"),
                            equalTo(DB_OPERATION, "EXEC"))
                        .hasParent(trace.getSpan(0))));
  }

  @Test
  void atomicBatchSingleCommand() {
    assumeStableAtomicBatchSupport();
    RBatch batch =
        redisson.createBatch(
            BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.REDIS_WRITE_ATOMIC));
    batch.getBucket("batch1").setAsync("v1");
    batch.execute();
    assertStableAtomicBatch("SET", null, "SET batch1 ?");
  }

  @Test
  void atomicBatchBeforeRedisson372() {
    Assumptions.assumeTrue(emitStableDatabaseSemconv());
    try {
      RBatch.class.getMethod("atomic");
      Class.forName("org.redisson.api.BatchOptions$ExecutionMode");
      Assumptions.abort();
    } catch (NoSuchMethodException ignored) {
      Assumptions.abort();
    } catch (ClassNotFoundException ignored) {
      // Atomic mode was passed to executeAsync before it became a CommandBatchService field.
    }

    RBatch batch = createBatch(redisson).atomic();
    batch.getBucket("batch1").setAsync("v1");
    batch.getBucket("batch2").setAsync("v2");
    batch.execute();
    assertStableAtomicBatch("MULTI SET", 2L, "SET batch1 ?; SET batch2 ?");
  }

  @Test
  void atomicBatchCannotExecuteTwice() {
    assumeStableAtomicBatchSupport();
    RBatch batch =
        redisson.createBatch(
            BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.REDIS_WRITE_ATOMIC));
    batch.getBucket("batch1").setAsync("v1");
    batch.execute();

    assertThat(catchThrowable(batch::execute)).isNotNull();
    assertStableAtomicBatch("SET", null, "SET batch1 ?");
  }

  @Test
  void atomicBatchMixedCommands() {
    assumeStableAtomicBatchSupport();
    RBatch batch =
        redisson.createBatch(
            BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.REDIS_WRITE_ATOMIC));
    batch.getBucket("batch1").setAsync("v1");
    batch.getBucket("batch1").getAsync();
    batch.execute();
    assertStableAtomicBatch("MULTI", 2L, "SET batch1 ?; GET batch1");
  }

  @Test
  void atomicBatchAsyncCommand() {
    assumeStableAtomicBatchSupport();
    RBatch batch =
        redisson.createBatch(
            BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.REDIS_WRITE_ATOMIC));
    batch.getBucket("batch1").setAsync("v1");
    batch.getBucket("batch2").setAsync("v2");
    batch.executeAsync().toCompletableFuture().join();
    assertStableAtomicBatch("MULTI SET", 2L, "SET batch1 ?; SET batch2 ?");
  }

  @Test
  @Tag(TEST_SINGLE_CONNECTION)
  void atomicBatchDiscard() throws ReflectiveOperationException {
    assumeStableAtomicBatchSupport();
    try {
      RBatch.class.getMethod("discard");
    } catch (NoSuchMethodException ignored) {
      Assumptions.abort();
    }

    RBatch batch =
        redisson.createBatch(
            BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.REDIS_WRITE_ATOMIC));
    batch.getBucket("batch1").setAsync("v1");
    batch.getClass().getMethod("discard").invoke(batch);

    // Verify that DISCARD clears suppression state from the pooled connection.
    redisson.getBucket("after-discard").get();
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("GET " + address).hasKind(CLIENT).hasNoParent()));
  }

  @Test
  void atomicBatchTruncatesQueryText() {
    assumeStableAtomicBatchSupport();
    String bucketName = "bucket" + String.join("", nCopies(15_000, "a"));
    int batchSize = 4;
    RBatch batch =
        redisson.createBatch(
            BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.REDIS_WRITE_ATOMIC));
    for (int i = 0; i < batchSize; i++) {
      batch.getBucket(bucketName).setAsync("v" + i);
    }
    batch.execute();
    assertStableAtomicBatch(
        "MULTI SET", (long) batchSize, String.join("; ", nCopies(2, "SET " + bucketName + " ?")));
  }

  @Test
  @Tag(TEST_SINGLE_CONNECTION)
  void atomicBatchFailure() {
    assumeStableAtomicBatchSupport();
    redisson.getBucket("wrongtype").set("value");
    testing.clearData();

    RBatch batch =
        redisson.createBatch(
            BatchOptions.defaults().executionMode(BatchOptions.ExecutionMode.REDIS_WRITE_ATOMIC));
    batch.getMap("wrongtype").getAsync("field");
    batch.getBucket("after").setAsync("value");

    Throwable error = catchThrowable(batch::execute);
    assertThat(error).isNotNull();
    redisson.getBucket("after-failure").set("value");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("MULTI")
                        .hasKind(CLIENT)
                        .hasStatus(StatusData.error())
                        .hasException(error)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_OPERATION_NAME, "MULTI"),
                            equalTo(DB_OPERATION_BATCH_SIZE, 2L),
                            equalTo(ERROR_TYPE, error.getClass().getName()),
                            equalTo(DB_QUERY_TEXT, "HGET wrongtype field; SET after ?"),
                            equalTo(DB_SYSTEM, emitOldDatabaseSemconv() ? REDIS : null),
                            equalTo(DB_OPERATION, emitOldDatabaseSemconv() ? "MULTI" : null),
                            equalTo(
                                DB_STATEMENT,
                                emitOldDatabaseSemconv()
                                    ? "HGET wrongtype field; SET after ?"
                                    : null))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("SET " + address).hasKind(CLIENT).hasNoParent()));
  }

  private static void assumeStableAtomicBatchSupport() {
    Assumptions.assumeTrue(emitStableDatabaseSemconv());
    try {
      Class.forName("org.redisson.api.BatchOptions$ExecutionMode");
    } catch (ClassNotFoundException ignored) {
      Assumptions.abort();
    }
  }

  private static void assertStableAtomicBatch(
      String operationName, Long batchSize, String queryText) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(operationName)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_OPERATION_NAME, operationName),
                            equalTo(DB_OPERATION_BATCH_SIZE, batchSize),
                            equalTo(DB_QUERY_TEXT, queryText),
                            equalTo(DB_SYSTEM, emitOldDatabaseSemconv() ? REDIS : null),
                            equalTo(DB_OPERATION, emitOldDatabaseSemconv() ? operationName : null),
                            equalTo(DB_STATEMENT, emitOldDatabaseSemconv() ? queryText : null))));
  }

  @Test
  void listCommand() {
    RList<String> strings = redisson.getList("list1");
    strings.add("a");

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.CLIENT),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "RPUSH " + address : "RPUSH")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "RPUSH list1 ?"),
                            equalTo(maybeStable(DB_OPERATION), "RPUSH"))
                        .hasNoParent()));
  }

  @Test
  void hashCommand() {
    RMap<String, String> map = redisson.getMap("map1");
    map.put("key1", "value1");
    map.get("key1");

    String script =
        "local v = redis.call('hget', KEYS[1], ARGV[1]); redis.call('hset', KEYS[1], ARGV[1], ARGV[2]); return v";
    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "EVAL " + address : "EVAL")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                String.format("EVAL %s 1 map1 ? ?", script)),
                            equalTo(maybeStable(DB_OPERATION), "EVAL"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "HGET " + address : "HGET")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "HGET map1 key1"),
                            equalTo(maybeStable(DB_OPERATION), "HGET"))));
  }

  @Test
  void setCommand() {
    RSet<String> set = redisson.getSet("set1");
    set.add("s1");

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "SADD " + address : "SADD")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "SADD set1 ?"),
                            equalTo(maybeStable(DB_OPERATION), "SADD"))));
  }

  @Test
  void sortedSetCommand() throws ReflectiveOperationException {
    Map<String, Double> scores = new HashMap<>();
    scores.put("u1", 1.0d);
    scores.put("u2", 3.0d);
    scores.put("u3", 0.0d);
    RScoredSortedSet<String> sortSet = redisson.getScoredSortedSet("sort_set1");
    //  Adapt different method signature:
    // `Long addAll(Map<V, Double> objects);` and `int addAll(Map<V, Double> objects);`
    invokeAddAll(sortSet, scores);

    testing.waitAndAssertSortedTraces(
        orderByRootSpanName(emitStableDatabaseSemconv() ? "ZADD " + address : "ZADD"),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "ZADD " + address : "ZADD")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "ZADD sort_set1 ? ? ? ? ? ?"),
                            equalTo(maybeStable(DB_OPERATION), "ZADD"))));
  }

  private static void invokeAddAll(RScoredSortedSet<String> object, Map<String, Double> arg)
      throws ReflectiveOperationException {
    object.getClass().getMethod("addAll", Map.class).invoke(object, arg);
  }

  @Test
  void atomicLongCommand() {
    RAtomicLong atomicLong = redisson.getAtomicLong("AtomicLong");
    atomicLong.incrementAndGet();

    testing.waitAndAssertSortedTraces(
        orderByRootSpanKind(SpanKind.INTERNAL, SpanKind.CLIENT),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "INCR " + address : "INCR")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_STATEMENT), "INCR AtomicLong"),
                            equalTo(maybeStable(DB_OPERATION), "INCR"))));
  }

  @Test
  void lockCommand() {
    RLock lock = redisson.getLock("lock");
    lock.lock();
    try {
      logger.info("enter lock block");
    } finally {
      lock.unlock();
    }

    List<Consumer<TraceAssert>> traceAsserts = new ArrayList<>();
    traceAsserts.add(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "EVAL " + address : "EVAL")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_OPERATION), "EVAL"),
                            satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("EVAL")))));
    traceAsserts.add(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "EVAL " + address : "EVAL")
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(maybeStable(DB_OPERATION), "EVAL"),
                            satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("EVAL")))));
    if (lockHas3Traces()) {
      traceAsserts.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName(emitStableDatabaseSemconv() ? "DEL " + address : "DEL")
                          .hasKind(CLIENT)
                          .hasAttributesSatisfyingExactly(
                              equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                              equalTo(NETWORK_PEER_ADDRESS, ip),
                              equalTo(NETWORK_PEER_PORT, port),
                              equalTo(SERVER_ADDRESS, host),
                              equalTo(SERVER_PORT, port),
                              equalTo(maybeStable(DB_SYSTEM), REDIS),
                              equalTo(maybeStable(DB_OPERATION), "DEL"),
                              satisfies(maybeStable(DB_STATEMENT), val -> val.startsWith("DEL")))));
    }

    testing.waitAndAssertSortedTraces(orderByRootSpanKind(SpanKind.CLIENT), traceAsserts);
  }

  protected boolean useRedisProtocol() {
    return testLatestDeps();
  }

  protected boolean lockHas3Traces() {
    return false;
  }

  protected RBatch createBatch(RedissonClient redisson) {
    return redisson.createBatch(BatchOptions.defaults());
  }

  private static final class BatchScenario {
    final List<Consumer<RBatch>> commands;
    final String operationName;
    final String oldSpanName;
    final Long batchSize;
    final String oldOperation;
    final String queryText;
    final boolean empty;

    BatchScenario(Builder builder) {
      this.commands = builder.commands;
      this.operationName = builder.operationName;
      this.oldSpanName = builder.oldSpanName;
      this.batchSize = builder.batchSize;
      this.oldOperation = builder.oldOperation;
      this.queryText = builder.queryText;
      this.empty = builder.empty;
    }

    static Builder builder() {
      return new Builder();
    }

    static final class Builder {
      private final List<Consumer<RBatch>> commands = new ArrayList<>();
      private String operationName;
      private String oldSpanName;
      private Long batchSize;
      private String oldOperation;
      private String queryText;
      private boolean empty;

      Builder addCommand(Consumer<RBatch> addCommand) {
        this.commands.add(addCommand);
        return this;
      }

      Builder operationName(String operationName) {
        this.operationName = operationName;
        return this;
      }

      Builder oldSpanName(String oldSpanName) {
        this.oldSpanName = oldSpanName;
        return this;
      }

      Builder batchSize(long batchSize) {
        this.batchSize = batchSize;
        return this;
      }

      Builder oldOperation(String oldOperation) {
        this.oldOperation = oldOperation;
        return this;
      }

      Builder queryText(String queryText) {
        this.queryText = queryText;
        return this;
      }

      Builder empty() {
        this.empty = true;
        return this;
      }

      BatchScenario build() {
        return new BatchScenario(this);
      }
    }
  }
}
