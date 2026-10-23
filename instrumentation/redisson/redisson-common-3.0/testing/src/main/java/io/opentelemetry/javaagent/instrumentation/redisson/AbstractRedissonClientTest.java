/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.api.trace.SpanKind.INTERNAL;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanKind;
import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static java.util.Collections.nCopies;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
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
import org.redisson.api.RFuture;
import org.redisson.api.RList;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RScript;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.RedisOutOfMemoryException;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.Decoder;
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
    // the stringCommandLazyConnection test case simulates reconnection during Redis command
    // execution, which needs an empty idle connection pool
    Integer connectionMinimumIdleSize = testInfo.getTags().contains(TEST_RECONNECT) ? 0 : null;
    redisson = Redisson.create(createConfig(0, connectionMinimumIdleSize));
    testing.clearData();
  }

  private Config createConfig(int database, Integer connectionMinimumIdleSize)
      throws InvocationTargetException, IllegalAccessException {
    Config config = new Config();
    try {
      // script cache is enabled by default in 3.46.0 and that causes hashCommand and lockCommand
      // tests to fail
      Config.class.getMethod("setUseScriptCache", boolean.class).invoke(config, false);
    } catch (NoSuchMethodException ignored) {
      // ignored
    }
    SingleServerConfig singleServerConfig = config.useSingleServer();
    singleServerConfig.setAddress(redisAddressForHost(host));
    singleServerConfig.setTimeout(30_000);
    singleServerConfig.setDatabase(database);
    if (connectionMinimumIdleSize != null) {
      singleServerConfig.setConnectionMinimumIdleSize(connectionMinimumIdleSize);
    }
    try {
      // disable connection ping if it exists
      singleServerConfig
          .getClass()
          .getMethod("setPingConnectionInterval", int.class)
          .invoke(singleServerConfig, 0);
    } catch (NoSuchMethodException ignored) {
      // ignored
    }
    return config;
  }

  private String redisAddressForHost(String serverHost) {
    // Newer versions of redisson require scheme, older versions forbid it.
    return (useRedisProtocol() ? "redis://" : "") + serverHost + ":" + port;
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
                    span.hasName("SET " + address)
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_QUERY_TEXT, "SET foo ?"),
                            equalTo(DB_OPERATION_NAME, "SET")),
                span ->
                    span.hasName("GET " + address)
                        .hasKind(CLIENT)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_QUERY_TEXT, "GET foo"),
                            equalTo(DB_OPERATION_NAME, "GET"))));
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
                  span.hasName("SET " + address)
                      .hasKind(CLIENT)
                      .hasAttributesSatisfyingExactly(
                          equalTo(NETWORK_PEER_ADDRESS, ip),
                          equalTo(NETWORK_PEER_PORT, port),
                          equalTo(SERVER_ADDRESS, host),
                          equalTo(SERVER_PORT, port),
                          equalTo(DB_SYSTEM_NAME, REDIS),
                          equalTo(DB_NAMESPACE, dbNamespace("0")),
                          equalTo(DB_QUERY_TEXT, "SET foo ?"),
                          equalTo(DB_OPERATION_NAME, "SET")));
        });

    List<AttributeKey<?>> expectedKeys =
        new ArrayList<>(
            asList(
                DB_SYSTEM_NAME,
                DB_OPERATION_NAME,
                NETWORK_PEER_PORT,
                NETWORK_PEER_ADDRESS,
                SERVER_PORT,
                SERVER_ADDRESS));
    if (hasDatabaseIndex()) {
      expectedKeys.add(DB_NAMESPACE);
    }
    assertDurationMetric(
        testing, instrumentationName.get(), expectedKeys.toArray(new AttributeKey<?>[0]));
  }

  @Test
  void wrongTypeErrorIsRecordedInSpanAndMetrics() throws IOException, InterruptedException {
    assertThat(
            redisServer.execInContainer("redis-cli", "lpush", "wrong-type", "value").getExitCode())
        .isZero();
    assertThatThrownBy(() -> redisson.getBucket("wrong-type").get())
        .isInstanceOf(RedisException.class);

    AtomicReference<String> instrumentationName = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          instrumentationName.set(trace.getSpan(0).getInstrumentationScopeInfo().getName());
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("GET " + address)
                      .hasKind(CLIENT)
                      .hasAttributesSatisfyingExactly(
                          equalTo(NETWORK_PEER_ADDRESS, ip),
                          equalTo(NETWORK_PEER_PORT, port),
                          equalTo(SERVER_ADDRESS, host),
                          equalTo(SERVER_PORT, port),
                          equalTo(DB_SYSTEM_NAME, REDIS),
                          equalTo(DB_NAMESPACE, dbNamespace("0")),
                          equalTo(DB_QUERY_TEXT, "GET wrong-type"),
                          equalTo(DB_OPERATION_NAME, "GET"),
                          equalTo(ERROR_TYPE, "WRONGTYPE")));
        });

    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        metrics ->
            metrics
                .hasName("db.client.operation.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point.hasAttributesSatisfying(
                                    attributes ->
                                        assertThat(attributes.asMap())
                                            .containsEntry(ERROR_TYPE, "WRONGTYPE")))));
  }

  @Test
  void serverErrorPrefixContract() {
    assertScriptErrorType("OOM command not allowed", "OOM", RedisOutOfMemoryException.class);
    assertScriptErrorType("Custom.Code! diagnostic details", "Custom.Code!", null);
    assertScriptErrorType("Préfix diagnostic", "Préfix", null);
    assertScriptErrorType("", RedisException.class.getName(), null);

    Object result =
        redisson.getScript().eval(RScript.Mode.READ_ONLY, "return 1", RScript.ReturnType.VALUE);
    assertThat(result).isEqualTo(1L);
    assertErrorTypeOnSpanAndMetric(null);
  }

  @Test
  void reentrantFailureCallbackKeepsClientErrorFallback()
      throws InterruptedException, ReflectiveOperationException {
    Assumptions.assumeTrue(isClassAvailable("org.redisson.config.CommandMapper"));

    Class<?> commandMapperType = loadCommandMapperType();
    Config config = createConfig(0, null);
    config.useSingleServer().setConnectionPoolSize(1).setConnectionMinimumIdleSize(1);

    CountDownLatch evalMappingStarted = new CountDownLatch(1);
    CountDownLatch allowEvalMapping = new CountDownLatch(1);
    AtomicBoolean interceptCommands = new AtomicBoolean();
    AtomicBoolean failureCallbackActive = new AtomicBoolean();
    AtomicBoolean getMappedInFailureCallback = new AtomicBoolean();
    Object commandMapper =
        Proxy.newProxyInstance(
            commandMapperType.getClassLoader(),
            new Class<?>[] {commandMapperType},
            (proxy, method, args) -> {
              if (method.getName().equals("map")) {
                String command = (String) args[0];
                if (!interceptCommands.get()) {
                  return command;
                }
                if (command.equals("EVAL") || command.equals("EVALSHA")) {
                  evalMappingStarted.countDown();
                  try {
                    if (!allowEvalMapping.await(30, SECONDS)) {
                      throw new RedisException("timed out waiting to release command mapping");
                    }
                  } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RedisException("interrupted while mapping command", e);
                  }
                } else if (command.equals("GET")) {
                  getMappedInFailureCallback.set(failureCallbackActive.get());
                  throw new RedisException("client-side command mapper failure");
                }
                return command;
              }
              return null;
            });
    setCommandMapper(config, commandMapperType, commandMapper);

    RedissonClient callbackClient = Redisson.create(config);
    interceptCommands.set(true);
    testing.clearData();
    try {
      testing.runWithSpan(
          "parent",
          () -> {
            RFuture<?> serverError =
                callbackClient
                    .getScript()
                    .evalAsync(
                        RScript.Mode.READ_WRITE,
                        "return redis.error_reply('Custom.Callback! diagnostic details')",
                        RScript.ReturnType.VALUE);
            assertThat(evalMappingStarted.await(30, SECONDS)).isTrue();

            AtomicBoolean failureCallbackCalled = new AtomicBoolean();
            AtomicReference<RFuture<?>> clientError = new AtomicReference<>();
            CompletionStage<?> failureCallback =
                serverError.whenComplete(
                    (result, error) -> {
                      failureCallbackCalled.set(true);
                      failureCallbackActive.set(true);
                      try {
                        clientError.set(
                            callbackClient.getBucket("callback-client-error").getAsync());
                      } finally {
                        failureCallbackActive.set(false);
                      }
                    });
            allowEvalMapping.countDown();

            assertThatThrownBy(() -> failureCallback.toCompletableFuture().join())
                .hasRootCauseInstanceOf(RedisException.class);
            assertThat(failureCallbackCalled).isTrue();
            assertThat(getMappedInFailureCallback).isTrue();
            assertThat((Object) clientError.get()).isNotNull();
            assertThatThrownBy(() -> clientError.get().toCompletableFuture().join())
                .hasRootCauseInstanceOf(RedisException.class);
            callbackClient.getBucket("callback-success").set("success");
          });
    } finally {
      allowEvalMapping.countDown();
      callbackClient.shutdown();
    }

    AtomicReference<String> instrumentationName = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          instrumentationName.set(trace.getSpan(1).getInstrumentationScopeInfo().getName());
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("parent").hasKind(INTERNAL).hasNoParent(),
              span ->
                  span.hasName("EVAL " + address)
                      .hasKind(CLIENT)
                      .hasAttributesSatisfying(
                          attributes ->
                              assertThat(attributes.asMap())
                                  .containsEntry(ERROR_TYPE, "Custom.Callback!")),
              span ->
                  span.hasName("GET " + address)
                      .hasKind(CLIENT)
                      .hasAttributesSatisfying(
                          attributes ->
                              assertThat(attributes.asMap())
                                  .containsEntry(ERROR_TYPE, RedisException.class.getName())),
              span ->
                  span.hasName("SET " + address)
                      .hasKind(CLIENT)
                      .hasAttributesSatisfying(
                          attributes ->
                              assertThat(attributes.asMap()).doesNotContainKey(ERROR_TYPE)));
        });
    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        metrics ->
            metrics
                .hasName("db.client.operation.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram
                            .satisfies(data -> assertThat(data.getPoints()).hasSize(3))
                            .hasPointsSatisfying(
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasAttributesSatisfying(
                                            attributes ->
                                                assertThat(attributes.asMap())
                                                    .containsEntry(ERROR_TYPE, "Custom.Callback!")),
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasAttributesSatisfying(
                                            attributes ->
                                                assertThat(attributes.asMap())
                                                    .containsEntry(
                                                        ERROR_TYPE,
                                                        RedisException.class.getName())),
                                point ->
                                    point
                                        .hasCount(1)
                                        .hasAttributesSatisfying(
                                            attributes ->
                                                assertThat(attributes.asMap())
                                                    .doesNotContainKey(ERROR_TYPE)))));
  }

  private static Class<?> loadCommandMapperType() throws ClassNotFoundException {
    return Class.forName(
        "org.redisson.config.CommandMapper",
        false,
        AbstractRedissonClientTest.class.getClassLoader());
  }

  private static void setCommandMapper(
      Config config, Class<?> commandMapperType, Object commandMapper)
      throws ReflectiveOperationException {
    config
        .getClass()
        .getMethod("setCommandMapper", commandMapperType)
        .invoke(config, commandMapper);
  }

  @Test
  void failedBatchErrorPrefixIsRecordedInSpanAndMetrics() throws ReflectiveOperationException {
    RBatch batch = createBatch(redisson);
    batch
        .getScript()
        .evalAsync(
            RScript.Mode.READ_WRITE,
            "return redis.error_reply('Batch.Code! diagnostic')",
            RScript.ReturnType.VALUE);

    assertThatThrownBy(() -> invokeExecute(batch)).hasRootCauseInstanceOf(RedisException.class);

    assertErrorTypeOnSpanAndMetric("Batch.Code!");
  }

  @Test
  void batchRedirectIsRetriedAndPreservesBatchResult()
      throws IOException, InterruptedException, ReflectiveOperationException {
    Assumptions.assumeTrue(isClassAvailable("org.redisson.api.BatchOptions"));
    GenericContainer<?> redirectServer =
        new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);
    redirectServer.start();
    try {
      String key = "batch-redirect-" + UUID.randomUUID();
      assertThat(redirectServer.execInContainer("redis-cli", "incr", key).getExitCode()).isZero();
      String script =
          "local count = redis.call('INCR', '"
              + key
              + "'); if count == 1 then return redis.error_reply('MOVED 1234 "
              + redirectServer.getHost()
              + ":"
              + redirectServer.getMappedPort(6379)
              + "'); end; return count";
      RBatch batch = createBatch(redisson);
      batch.getScript().evalAsync(RScript.Mode.READ_WRITE, script, RScript.ReturnType.VALUE);

      assertThat(batchResponses(invokeExecute(batch))).isEqualTo(asList(2L));
      assertRedirectErrorTypeOnAttempts();
      assertFollowingOperationsHaveNoLeakedServerError();
    } finally {
      redirectServer.stop();
    }
  }

  @Test
  void batchRetryErrorPrefixIsCapturedBeforeCompletion()
      throws IOException, InterruptedException, ReflectiveOperationException {
    Assumptions.assumeTrue(isClassAvailable("org.redisson.api.BatchOptions"));
    Assumptions.assumeTrue(isClassAvailable("org.redisson.client.RedisRetryException"));
    Class<?> redisRetryException;
    try {
      redisRetryException = Class.forName("org.redisson.client.RedisRetryException");
    } catch (ClassNotFoundException ignored) {
      Assumptions.abort();
      return;
    }
    assertBatchServerErrorPrefix("LOADING Redis is loading", "LOADING", redisRetryException);
    assertFollowingOperationsHaveNoLeakedServerError();
  }

  @Test
  void clientSideDecodeFailureKeepsExceptionClassFallback()
      throws IOException, InterruptedException {
    assertClientSideDecodeFailure("client-side-decode-error");
  }

  private void assertBatchServerErrorPrefix(
      String serverError, String expectedErrorType, Class<?> expectedFailureType)
      throws ReflectiveOperationException {
    RBatch batch = createBatchWithoutRetries();
    batch
        .getScript()
        .evalAsync(
            RScript.Mode.READ_WRITE,
            "return redis.error_reply('" + serverError + "')",
            RScript.ReturnType.VALUE);

    assertThatThrownBy(() -> invokeExecute(batch))
        .satisfies(error -> assertThat(rootCause(error)).isInstanceOf(expectedFailureType));
    assertErrorTypeOnSpanAndMetric(expectedErrorType);
  }

  private RBatch createBatchWithoutRetries() throws ReflectiveOperationException {
    Class<?> batchOptions = Class.forName("org.redisson.api.BatchOptions");
    Object options = batchOptions.getMethod("defaults").invoke(null);
    batchOptions.getMethod("retryAttempts", int.class).invoke(options, 0);
    return (RBatch)
        redisson.getClass().getMethod("createBatch", batchOptions).invoke(redisson, options);
  }

  private static List<?> batchResponses(Object result) throws ReflectiveOperationException {
    if (result instanceof List) {
      return (List<?>) result;
    }
    return (List<?>) result.getClass().getMethod("getResponses").invoke(result);
  }

  private void assertFollowingOperationsHaveNoLeakedServerError()
      throws IOException, InterruptedException {
    redisson.getBucket("after-batch-server-error").set("success");
    assertErrorTypeOnSpanAndMetric(null);
    assertClientSideDecodeFailure("client-side-after-batch-error");
  }

  private void assertClientSideDecodeFailure(String key) throws IOException, InterruptedException {
    assertThat(redisServer.execInContainer("redis-cli", "set", key, "value").getExitCode())
        .isZero();
    RBucket<String> bucket =
        redisson.getBucket(
            key,
            new StringCodec() {
              @Override
              public Decoder<Object> getValueDecoder() {
                return (buffer, state) -> {
                  throw new RedisException("ERR client-side decode failure");
                };
              }
            });
    assertThatThrownBy(bucket::get).isInstanceOf(RedisException.class);

    AtomicReference<String> instrumentationName = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          instrumentationName.set(trace.getSpan(0).getInstrumentationScopeInfo().getName());
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("GET " + address)
                      .hasKind(CLIENT)
                      .hasAttributesSatisfyingExactly(
                          equalTo(NETWORK_PEER_ADDRESS, ip),
                          equalTo(NETWORK_PEER_PORT, port),
                          equalTo(SERVER_ADDRESS, host),
                          equalTo(SERVER_PORT, port),
                          equalTo(DB_SYSTEM_NAME, REDIS),
                          equalTo(DB_NAMESPACE, dbNamespace("0")),
                          equalTo(DB_QUERY_TEXT, "GET " + key),
                          equalTo(DB_OPERATION_NAME, "GET"),
                          equalTo(ERROR_TYPE, RedisException.class.getName())));
        });

    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        metrics ->
            metrics
                .hasName("db.client.operation.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point
                                    .hasCount(1)
                                    .hasAttributesSatisfying(
                                        attributes ->
                                            assertThat(attributes.asMap())
                                                .containsEntry(
                                                    ERROR_TYPE, RedisException.class.getName())))));
    testing.clearData();
  }

  private static Throwable rootCause(Throwable error) {
    while (error.getCause() != null && error.getCause() != error) {
      error = error.getCause();
    }
    return error;
  }

  private static boolean isClassAvailable(String name) {
    try {
      Class.forName(name, false, AbstractRedissonClientTest.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException ignored) {
      return false;
    }
  }

  private void assertScriptErrorType(
      String serverError, String expectedErrorType, Class<? extends RedisException> expectedCause) {
    String script = "return redis.error_reply('" + serverError + "')";
    assertThatThrownBy(
            () ->
                redisson
                    .getScript()
                    .eval(RScript.Mode.READ_WRITE, script, RScript.ReturnType.VALUE))
        .satisfies(
            error -> {
              if (expectedCause != null) {
                assertThat(error)
                    .satisfiesAnyOf(
                        e -> assertThat(e).isInstanceOf(expectedCause),
                        e -> assertThat(e).hasRootCauseInstanceOf(expectedCause));
              }
            });
    assertErrorTypeOnSpanAndMetric(expectedErrorType);
  }

  private static void assertErrorTypeOnSpanAndMetric(String expectedErrorType) {
    AtomicReference<String> instrumentationName = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          instrumentationName.set(trace.getSpan(0).getInstrumentationScopeInfo().getName());
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasKind(CLIENT)
                      .hasAttributesSatisfying(
                          attributes -> {
                            if (expectedErrorType == null) {
                              assertThat(attributes.asMap()).doesNotContainKey(ERROR_TYPE);
                            } else {
                              assertThat(attributes.asMap())
                                  .containsEntry(ERROR_TYPE, expectedErrorType);
                            }
                          }));
        });
    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        metrics ->
            metrics
                .hasName("db.client.operation.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point
                                    .hasCount(1)
                                    .hasAttributesSatisfying(
                                        attributes -> {
                                          if (expectedErrorType == null) {
                                            assertThat(attributes.asMap())
                                                .doesNotContainKey(ERROR_TYPE);
                                          } else {
                                            assertThat(attributes.asMap())
                                                .containsEntry(ERROR_TYPE, expectedErrorType);
                                          }
                                        }))));
    testing.clearData();
  }

  private static void assertRedirectErrorTypeOnAttempts() {
    AtomicReference<String> instrumentationName = new AtomicReference<>();
    testing.waitAndAssertTraces(
        trace -> {
          instrumentationName.set(trace.getSpan(0).getInstrumentationScopeInfo().getName());
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasKind(CLIENT)
                      .hasAttributesSatisfying(
                          attributes ->
                              assertThat(attributes.asMap()).containsEntry(ERROR_TYPE, "MOVED")));
        },
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasKind(CLIENT)
                        .hasAttributesSatisfying(
                            attributes ->
                                assertThat(attributes.asMap()).doesNotContainKey(ERROR_TYPE))));
    testing.waitAndAssertMetrics(
        instrumentationName.get(),
        metrics ->
            metrics
                .hasName("db.client.operation.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point
                                    .hasCount(1)
                                    .hasAttributesSatisfying(
                                        attributes ->
                                            assertThat(attributes.asMap())
                                                .containsEntry(ERROR_TYPE, "MOVED")),
                            point ->
                                point
                                    .hasCount(1)
                                    .hasAttributesSatisfying(
                                        attributes ->
                                            assertThat(attributes.asMap())
                                                .doesNotContainKey(ERROR_TYPE)))));
    testing.clearData();
  }

  @Test
  void configuredDatabaseIndex() throws InvocationTargetException, IllegalAccessException {
    RedissonClient databaseOne = Redisson.create(createConfig(1, null));
    try {
      testing.clearData();
      RBucket<String> keyObject = databaseOne.getBucket("foo");
      keyObject.set("bar");

      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("SET " + address)
                          .hasKind(CLIENT)
                          .hasAttributesSatisfyingExactly(
                              equalTo(NETWORK_PEER_ADDRESS, ip),
                              equalTo(NETWORK_PEER_PORT, port),
                              equalTo(SERVER_ADDRESS, host),
                              equalTo(SERVER_PORT, port),
                              equalTo(DB_SYSTEM_NAME, REDIS),
                              equalTo(DB_NAMESPACE, dbNamespace("1")),
                              equalTo(DB_QUERY_TEXT, "SET foo ?"),
                              equalTo(DB_OPERATION_NAME, "SET"))));
    } finally {
      databaseOne.shutdown();
    }
  }

  @Test
  void configuredMasterSlaveServerTarget() {
    String aliasHost = host.equals(ip) ? "localhost" : ip;
    String configuredServerAddress = host + ":" + port + "," + aliasHost + ":" + port;

    Config config = new Config();
    config
        .useMasterSlaveServers()
        .setMasterAddress(redisAddressForHost(host))
        .addSlaveAddress(redisAddressForHost(aliasHost));
    RedissonClient configuredClient = Redisson.create(config);
    try {
      if (hasDatabaseIndex()) {
        testing.waitForTraces(2);
      }
      assertConfiguredTarget(
          configuredClient, "SET " + configuredServerAddress, configuredServerAddress, null);
    } finally {
      configuredClient.shutdown();
    }
  }

  @Test
  void configuredSingleServerTarget() {
    String configuredHost = host.equals(ip) ? "localhost" : ip;
    Config config = new Config();
    config.useSingleServer().setAddress(redisAddressForHost(configuredHost));
    RedissonClient configuredClient = Redisson.create(config);
    try {
      if (hasDatabaseIndex()) {
        testing.waitForTraces(1);
      }
      assertConfiguredTarget(
          configuredClient, "SET " + configuredHost + ":" + port, configuredHost, port);
    } finally {
      configuredClient.shutdown();
    }
  }

  private void assertConfiguredTarget(
      RedissonClient client,
      String expectedSpanName,
      String expectedServerAddress,
      Long expectedServerPort) {
    testing.clearData();
    client.getBucket("configured-target").set("value");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(expectedSpanName)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, expectedServerAddress),
                            equalTo(SERVER_PORT, expectedServerPort),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_QUERY_TEXT, "SET configured-target ?"),
                            equalTo(DB_OPERATION_NAME, "SET"))));
  }

  @Test
  void stringCommand() {
    RBucket<String> keyObject = redisson.getBucket("foo");
    keyObject.set("bar");
    keyObject.get();
    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("SET " + address, "GET " + address),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_QUERY_TEXT, "SET foo ?"),
                            equalTo(DB_OPERATION_NAME, "SET"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_QUERY_TEXT, "GET foo"),
                            equalTo(DB_OPERATION_NAME, "GET"))));
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
                    span.hasName(scenario.operationName + " " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_OPERATION_NAME, scenario.operationName),
                            equalTo(DB_OPERATION_BATCH_SIZE, scenario.batchSize),
                            equalTo(DB_QUERY_TEXT, scenario.queryText))));
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
                .queryText("SET batch1 ?")
                .build()),
        argumentSet(
            "twoSameOperation",
            BatchScenario.builder()
                .addCommand(batch -> batch.getBucket("batch1").setAsync("v1"))
                .addCommand(batch -> batch.getBucket("batch2").setAsync("v2"))
                .operationName("PIPELINE SET")
                .batchSize(2)
                .queryText("SET batch1 ?; SET batch2 ?")
                .build()),
        argumentSet(
            "twoDifferentOperations",
            BatchScenario.builder()
                .addCommand(batch -> batch.getBucket("batch1").setAsync("v1"))
                .addCommand(batch -> batch.getBucket("batch1").getAsync())
                .operationName("PIPELINE")
                .batchSize(2)
                .queryText("SET batch1 ?; GET batch1")
                .build()));
  }

  private static Object invokeExecute(RBatch batch) throws ReflectiveOperationException {
    return batch.getClass().getMethod("execute").invoke(batch);
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
                    span.hasName("PIPELINE SET " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_OPERATION_NAME, "PIPELINE SET"),
                            equalTo(DB_OPERATION_BATCH_SIZE, (long) batchSize),
                            equalTo(
                                DB_QUERY_TEXT,
                                String.join(
                                    "; ",
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
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasName("parent").hasNoParent().hasKind(INTERNAL),
                span ->
                    span.hasName("MULTI SET " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_OPERATION_NAME, "MULTI SET"),
                            // db.operation.batch.size is not emitted because MULTI transaction
                            // telemetry is split across wrapper and command spans, so this span
                            // does not represent the full logical batch.
                            equalTo(DB_QUERY_TEXT, "MULTI; SET batch1 ?"))
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("SET " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_QUERY_TEXT, "SET batch2 ?"),
                            equalTo(DB_OPERATION_NAME, "SET"))
                        .hasParent(trace.getSpan(0)),
                span ->
                    span.hasName("EXEC " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_QUERY_TEXT, "EXEC"),
                            equalTo(DB_OPERATION_NAME, "EXEC"))
                        .hasParent(trace.getSpan(0))));
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
                    span.hasName("RPUSH " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_QUERY_TEXT, "RPUSH list1 ?"),
                            equalTo(DB_OPERATION_NAME, "RPUSH"))
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
                    span.hasName("EVAL " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_QUERY_TEXT, String.format("EVAL %s 1 map1 ? ?", script)),
                            equalTo(DB_OPERATION_NAME, "EVAL"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("HGET " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_QUERY_TEXT, "HGET map1 key1"),
                            equalTo(DB_OPERATION_NAME, "HGET"))));
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
                    span.hasName("SADD " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_QUERY_TEXT, "SADD set1 ?"),
                            equalTo(DB_OPERATION_NAME, "SADD"))));
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
        orderByRootSpanName("ZADD " + address),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("ZADD " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_QUERY_TEXT, "ZADD sort_set1 ? ? ? ? ? ?"),
                            equalTo(DB_OPERATION_NAME, "ZADD"))));
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
                    span.hasName("INCR " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_QUERY_TEXT, "INCR AtomicLong"),
                            equalTo(DB_OPERATION_NAME, "INCR"))));
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
                    span.hasName("EVAL " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_OPERATION_NAME, "EVAL"),
                            satisfies(DB_QUERY_TEXT, val -> val.startsWith("EVAL")))));
    traceAsserts.add(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("EVAL " + address)
                        .hasKind(CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, dbNamespace("0")),
                            equalTo(DB_OPERATION_NAME, "EVAL"),
                            satisfies(DB_QUERY_TEXT, val -> val.startsWith("EVAL")))));
    if (lockHas3Traces()) {
      traceAsserts.add(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("DEL " + address)
                          .hasKind(CLIENT)
                          .hasAttributesSatisfyingExactly(
                              equalTo(NETWORK_PEER_ADDRESS, ip),
                              equalTo(NETWORK_PEER_PORT, port),
                              equalTo(SERVER_ADDRESS, host),
                              equalTo(SERVER_PORT, port),
                              equalTo(DB_SYSTEM_NAME, REDIS),
                              equalTo(DB_NAMESPACE, dbNamespace("0")),
                              equalTo(DB_OPERATION_NAME, "DEL"),
                              satisfies(DB_QUERY_TEXT, val -> val.startsWith("DEL")))));
    }

    testing.waitAndAssertSortedTraces(orderByRootSpanKind(SpanKind.CLIENT), traceAsserts);
  }

  protected boolean useRedisProtocol() {
    return testLatestDeps();
  }

  protected boolean lockHas3Traces() {
    return false;
  }

  /** Whether the instrumented redisson version can report the Redis database index. */
  protected boolean hasDatabaseIndex() {
    return false;
  }

  private String dbNamespace(String databaseIndex) {
    return hasDatabaseIndex() ? databaseIndex : null;
  }

  protected RBatch createBatch(RedissonClient redisson) {
    return redisson.createBatch(BatchOptions.defaults());
  }

  private static final class BatchScenario {
    final List<Consumer<RBatch>> commands;
    final String operationName;
    final Long batchSize;
    final String queryText;
    final boolean empty;

    BatchScenario(Builder builder) {
      this.commands = builder.commands;
      this.operationName = builder.operationName;
      this.batchSize = builder.batchSize;
      this.queryText = builder.queryText;
      this.empty = builder.empty;
    }

    static Builder builder() {
      return new Builder();
    }

    static final class Builder {
      private final List<Consumer<RBatch>> commands = new ArrayList<>();
      private String operationName;
      private Long batchSize;
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

      Builder batchSize(long batchSize) {
        this.batchSize = batchSize;
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
