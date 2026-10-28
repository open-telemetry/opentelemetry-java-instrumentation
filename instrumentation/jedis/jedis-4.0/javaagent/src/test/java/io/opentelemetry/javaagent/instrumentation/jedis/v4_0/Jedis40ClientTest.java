/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
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
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Builder;
import redis.clients.jedis.BuilderFactory;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.CommandObject;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.DefaultJedisSocketFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisSocketFactory;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.Response;
import redis.clients.jedis.Transaction;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;
import redis.clients.jedis.exceptions.JedisException;
import redis.clients.jedis.providers.PooledConnectionProvider;

@SuppressWarnings("deprecation") // using deprecated semconv
class Jedis40ClientTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private static String host;

  private static String ip;

  private static int port;

  private static Jedis jedis;

  @BeforeAll
  static void setup() throws UnknownHostException {
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);
    port = redisServer.getMappedPort(6379);
    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    jedis = new Jedis(host, port);
    cleanup.deferAfterAll(jedis);
  }

  @BeforeEach
  void reset() {
    // jedis 4.x leaves the connection "in multi" after multi()/exec() (Transaction has no
    // back-reference to clear Jedis state), so reset it before reusing the shared client.
    jedis.resetState();
    jedis.select(0);
    jedis.flushAll();
    testing.waitForTraces(2);
    testing.clearData();
  }

  @Test
  void setCommand() {
    jedis.set("foo", "bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET foo ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.jedis-4.0",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_NAMESPACE,
        SERVER_ADDRESS,
        SERVER_PORT,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT);
  }

  @Test
  void wrongTypeErrorType() {
    jedis.lpush("wrong-type", "value");
    testing.waitForTraces(1);
    testing.clearData();

    assertThatThrownBy(() -> jedis.get("wrong-type")).hasMessageStartingWith("WRONGTYPE");

    assertErrorType("WRONGTYPE");
  }

  @Test
  void coldPooledCommandFailureIsAttributedToApplicationCommand() {
    jedis.lpush("cold-pool-wrong-type", "value");
    testing.waitForTraces(1);
    testing.clearData();

    JedisClientConfig clientConfig =
        DefaultJedisClientConfig.builder().clientName("otel-cold-pool-test").build();
    UnifiedJedis client =
        new UnifiedJedis(
            new PooledConnectionProvider(new HostAndPort(host, port), clientConfig),
            1,
            Duration.ZERO);
    cleanup.deferCleanup(client);

    assertThatThrownBy(() -> client.get("cold-pool-wrong-type"))
        .hasMessageStartingWith("WRONGTYPE");
    client.set("cold-pool-success", "value");

    boolean jedis51OrLater = Boolean.getBoolean("testJedis51OrLater");
    boolean latestJedis = testLatestDeps();
    int initializationCommands = jedis51OrLater ? 3 : 1;
    int expectedTraces = initializationCommands + 2 + (latestJedis ? 1 : 0);
    testing.waitForTraces(expectedTraces);

    assertThat(testing.spans())
        .hasSize(expectedTraces)
        .filteredOn(span -> "CLIENT".equals(span.getAttributes().get(DB_OPERATION_NAME)))
        .hasSize(initializationCommands)
        .filteredOn(
            span -> {
              String queryText = span.getAttributes().get(DB_QUERY_TEXT);
              return queryText != null && queryText.contains("SETINFO");
            })
        .hasSize(jedis51OrLater ? 2 : 0)
        .allSatisfy(span -> assertThat(span.getAttributes().get(ERROR_TYPE)).isEqualTo("ERR"));
    assertThat(testing.spans())
        .filteredOn(span -> "CLIENT".equals(span.getAttributes().get(DB_OPERATION_NAME)))
        .filteredOn(
            span -> {
              String queryText = span.getAttributes().get(DB_QUERY_TEXT);
              return queryText != null && queryText.contains("SETNAME");
            })
        .singleElement()
        .satisfies(span -> assertThat(span.getAttributes().get(ERROR_TYPE)).isNull());
    if (latestJedis) {
      assertThat(testing.spans())
          .filteredOn(span -> "HELLO".equals(span.getAttributes().get(DB_OPERATION_NAME)))
          .singleElement()
          .satisfies(span -> assertThat(span.getAttributes().get(ERROR_TYPE)).isNull());
    }
    assertThat(testing.spans())
        .filteredOn(span -> "GET".equals(span.getAttributes().get(DB_OPERATION_NAME)))
        .singleElement()
        .satisfies(span -> assertThat(span.getAttributes().get(ERROR_TYPE)).isEqualTo("WRONGTYPE"));
    assertThat(testing.spans())
        .filteredOn(span -> "SET".equals(span.getAttributes().get(DB_OPERATION_NAME)))
        .singleElement()
        .satisfies(span -> assertThat(span.getAttributes().get(ERROR_TYPE)).isNull());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-4.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric -> {
                  assertThat(metric.getHistogramData().getPoints())
                      .filteredOn(
                          point -> "CLIENT".equals(point.getAttributes().get(DB_OPERATION_NAME)))
                      .filteredOn(point -> "ERR".equals(point.getAttributes().get(ERROR_TYPE)))
                      .hasSize(jedis51OrLater ? 1 : 0)
                      .allSatisfy(point -> assertThat(point.getCount()).isEqualTo(2));
                  assertThat(metric.getHistogramData().getPoints())
                      .filteredOn(
                          point -> "CLIENT".equals(point.getAttributes().get(DB_OPERATION_NAME)))
                      .filteredOn(point -> point.getAttributes().get(ERROR_TYPE) == null)
                      .singleElement()
                      .satisfies(point -> assertThat(point.getCount()).isEqualTo(1));
                  assertThat(metric.getHistogramData().getPoints())
                      .filteredOn(
                          point -> "GET".equals(point.getAttributes().get(DB_OPERATION_NAME)))
                      .singleElement()
                      .satisfies(
                          point ->
                              assertThat(point.getAttributes().get(ERROR_TYPE))
                                  .isEqualTo("WRONGTYPE"));
                  if (latestJedis) {
                    assertThat(metric.getHistogramData().getPoints())
                        .filteredOn(
                            point -> "HELLO".equals(point.getAttributes().get(DB_OPERATION_NAME)))
                        .singleElement()
                        .satisfies(
                            point -> assertThat(point.getAttributes().get(ERROR_TYPE)).isNull());
                  }
                  assertThat(metric.getHistogramData().getPoints())
                      .filteredOn(
                          point -> "SET".equals(point.getAttributes().get(DB_OPERATION_NAME)))
                      .singleElement()
                      .satisfies(
                          point -> assertThat(point.getAttributes().get(ERROR_TYPE)).isNull());
                }));
  }

  @Test
  void coldPooledSelectFailureIsAttributedToSelect() throws Exception {
    String username = "jedis-out-of-range-db";
    String password = "password";
    assertThat(
            redisServer
                .execInContainer(
                    "redis-cli",
                    "ACL",
                    "SETUSER",
                    username,
                    "on",
                    ">" + password,
                    "allkeys",
                    "+@all")
                .getStdout()
                .trim())
        .isEqualTo("OK");
    cleanup.deferCleanup(
        () -> redisServer.execInContainer("redis-cli", "ACL", "DELUSER", username));

    JedisClientConfig clientConfig =
        DefaultJedisClientConfig.builder().user(username).password(password).database(1000).build();
    UnifiedJedis client =
        new UnifiedJedis(
            new PooledConnectionProvider(new HostAndPort(host, port), clientConfig),
            1,
            Duration.ZERO);
    cleanup.deferCleanup(client);

    assertThatThrownBy(() -> client.get("unreached"))
        .hasMessageStartingWith("ERR DB index is out of range");
    jedis.set("after-initialization-error", "value");

    boolean jedis51OrLater = Boolean.getBoolean("testJedis51OrLater");
    testing.waitForTraces(jedis51OrLater ? 5 : 4);
    String authenticationOperation = testLatestDeps() ? "HELLO" : "AUTH";
    assertThat(testing.spans())
        .filteredOn(
            span -> authenticationOperation.equals(span.getAttributes().get(DB_OPERATION_NAME)))
        .singleElement()
        .satisfies(span -> assertThat(span.getAttributes().get(ERROR_TYPE)).isNull());
    assertThat(testing.spans())
        .filteredOn(span -> "SELECT".equals(span.getAttributes().get(DB_OPERATION_NAME)))
        .singleElement()
        .satisfies(span -> assertThat(span.getAttributes().get(ERROR_TYPE)).isEqualTo("ERR"));
    assertThat(testing.spans())
        .filteredOn(span -> "SET".equals(span.getAttributes().get(DB_OPERATION_NAME)))
        .singleElement()
        .satisfies(span -> assertThat(span.getAttributes().get(ERROR_TYPE)).isNull());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-4.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric -> {
                  assertThat(metric.getHistogramData().getPoints())
                      .filteredOn(
                          point ->
                              authenticationOperation.equals(
                                  point.getAttributes().get(DB_OPERATION_NAME)))
                      .singleElement()
                      .satisfies(
                          point -> assertThat(point.getAttributes().get(ERROR_TYPE)).isNull());
                  assertThat(metric.getHistogramData().getPoints())
                      .filteredOn(
                          point -> "SELECT".equals(point.getAttributes().get(DB_OPERATION_NAME)))
                      .singleElement()
                      .satisfies(
                          point ->
                              assertThat(point.getAttributes().get(ERROR_TYPE)).isEqualTo("ERR"));
                  assertThat(metric.getHistogramData().getPoints())
                      .filteredOn(
                          point -> "SET".equals(point.getAttributes().get(DB_OPERATION_NAME)))
                      .singleElement()
                      .satisfies(
                          point -> assertThat(point.getAttributes().get(ERROR_TYPE)).isNull());
                }));
  }

  @Test
  void partialInitializationReplyDrainPreservesCompletedCommand() throws Exception {
    Assumptions.assumeTrue(Boolean.getBoolean("testJedis51OrLater") && !testLatestDeps());

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      try (ServerSocket server = new ServerSocket(0)) {
        Future<?> serverTask =
            executor.submit(
                () -> {
                  try (Socket socket = server.accept()) {
                    InputStream input = socket.getInputStream();
                    readRedisCommand(input);
                    readRedisCommand(input);
                    readRedisCommand(input);

                    OutputStream output = socket.getOutputStream();
                    output.write("+OK\r\n".getBytes(UTF_8));
                    output.flush();
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                });

        JedisClientConfig clientConfig =
            DefaultJedisClientConfig.builder().clientName("otel-partial-drain-test").build();
        UnifiedJedis client =
            new UnifiedJedis(
                new PooledConnectionProvider(
                    new HostAndPort("127.0.0.1", server.getLocalPort()), clientConfig),
                1,
                Duration.ZERO);
        cleanup.deferCleanup(client);

        assertThatThrownBy(() -> client.get("never")).isInstanceOf(JedisException.class);
        serverTask.get(10, SECONDS);
      }

      jedis.set("after-partial-initialization", "value");

      String connectionErrorType = JedisConnectionException.class.getName();
      testing.waitForTraces(4);
      assertThat(testing.spans()).hasSize(4);
      assertThat(testing.spans())
          .filteredOn(span -> "CLIENT".equals(span.getAttributes().get(DB_OPERATION_NAME)))
          .hasSize(3);
      assertThat(testing.spans())
          .filteredOn(span -> "CLIENT".equals(span.getAttributes().get(DB_OPERATION_NAME)))
          .filteredOn(
              span -> {
                String queryText = span.getAttributes().get(DB_QUERY_TEXT);
                return queryText != null && queryText.contains("SETNAME");
              })
          .singleElement()
          .satisfies(span -> assertThat(span.getAttributes().get(ERROR_TYPE)).isNull());
      assertThat(testing.spans())
          .filteredOn(span -> "CLIENT".equals(span.getAttributes().get(DB_OPERATION_NAME)))
          .filteredOn(
              span -> {
                String queryText = span.getAttributes().get(DB_QUERY_TEXT);
                return queryText != null && queryText.contains("SETINFO");
              })
          .hasSize(2)
          .allSatisfy(
              span ->
                  assertThat(span.getAttributes().get(ERROR_TYPE)).isEqualTo(connectionErrorType));
      assertThat(testing.spans())
          .filteredOn(span -> "SET".equals(span.getAttributes().get(DB_OPERATION_NAME)))
          .singleElement()
          .satisfies(span -> assertThat(span.getAttributes().get(ERROR_TYPE)).isNull());

      testing.waitAndAssertMetrics(
          "io.opentelemetry.jedis-4.0",
          "db.client.operation.duration",
          metrics ->
              metrics.anySatisfy(
                  metric -> {
                    assertThat(metric.getHistogramData().getPoints()).hasSize(3);
                    assertThat(metric.getHistogramData().getPoints())
                        .filteredOn(
                            point -> "CLIENT".equals(point.getAttributes().get(DB_OPERATION_NAME)))
                        .filteredOn(
                            point ->
                                connectionErrorType.equals(point.getAttributes().get(ERROR_TYPE)))
                        .singleElement()
                        .satisfies(point -> assertThat(point.getCount()).isEqualTo(2));
                    assertThat(metric.getHistogramData().getPoints())
                        .filteredOn(
                            point -> "CLIENT".equals(point.getAttributes().get(DB_OPERATION_NAME)))
                        .filteredOn(point -> point.getAttributes().get(ERROR_TYPE) == null)
                        .singleElement()
                        .satisfies(point -> assertThat(point.getCount()).isEqualTo(1));
                    assertThat(metric.getHistogramData().getPoints())
                        .filteredOn(
                            point -> "SET".equals(point.getAttributes().get(DB_OPERATION_NAME)))
                        .singleElement()
                        .satisfies(
                            point -> assertThat(point.getAttributes().get(ERROR_TYPE)).isNull());
                  }));
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void dedicatedServerErrorUsesExceptionClass() {
    Throwable error =
        catchThrowable(
            () ->
                jedis.eval(
                    "return redis.error_reply(ARGV[1])".getBytes(UTF_8),
                    0,
                    "MOVED 42 127.0.0.1:6379".getBytes(UTF_8)));

    assertThat(error).isInstanceOf(JedisDataException.class);
    assertThat(error.getClass()).isNotEqualTo(JedisDataException.class);
    assertErrorType(error.getClass().getName());
  }

  @Test
  void localCommandBuilderFailureUsesExceptionClass() {
    JedisDataException localFailure = new JedisDataException("WRONGTYPE local conversion failure");
    try (UnifiedJedis client =
        new UnifiedJedis(
            new PooledConnectionProvider(new HostAndPort(host, port)), 1, Duration.ZERO)) {
      client.executeCommand(
          new CommandObject<>(new CommandArguments(Protocol.Command.PING), BuilderFactory.STRING));
      testing.waitForTraces(Boolean.getBoolean("testJedis51OrLater") ? 3 : 1);
      testing.clearData();

      Throwable error =
          catchThrowable(
              () ->
                  client.executeCommand(
                      new CommandObject<>(
                          new CommandArguments(Protocol.Command.PING),
                          new Builder<String>() {
                            @Override
                            public String build(Object response) {
                              throw localFailure;
                            }
                          })));

      assertThat(error).isSameAs(localFailure);
      assertErrorType(JedisDataException.class.getName());
    }

    testing.waitForTraces(Boolean.getBoolean("testJedis51OrLater") ? 1 : 2);
    testing.clearData();
  }

  @Test
  void unreliableServerPrefixUsesExceptionClass() {
    Throwable error =
        catchThrowable(
            () ->
                jedis.eval(
                    "return redis.error_reply(ARGV[1])".getBytes(UTF_8),
                    0,
                    "échec details".getBytes(UTF_8)));

    assertThat(error).isInstanceOf(JedisDataException.class);
    assertErrorType(error.getClass().getName());
  }

  @Test
  void clientFailureUsesExceptionClassAndDoesNotLeak() {
    Jedis broken = new Jedis("127.0.0.1", 0);
    cleanup.deferCleanup(broken::disconnect);

    Throwable error = catchThrowable(() -> broken.get("key"));

    assertThat(error).isInstanceOf(JedisConnectionException.class);
    assertErrorType(error.getClass().getName());

    testing.clearData();
    jedis.set("after-error", "value");
    assertErrorType(null);
  }

  @Test
  void pipelineAndTransactionErrorsCompleteBatchSpans() {
    jedis.lpush("wrong-type", "value");
    jedis.set("not-number", "value");
    testing.waitForTraces(2);
    testing.clearData();

    Pipeline pipeline = jedis.pipelined();
    pipeline.get("wrong-type");
    pipeline.incr("not-number");
    assertThat(pipeline.syncAndReturnAll())
        .hasSize(2)
        .allSatisfy(result -> assertThat(result).isInstanceOf(JedisDataException.class));
    assertErrorType("WRONGTYPE");

    testing.clearData();
    Transaction transaction = jedis.multi();
    transaction.get("wrong-type");
    transaction.incr("not-number");
    assertThat(transaction.exec())
        .hasSize(2)
        .allSatisfy(result -> assertThat(result).isInstanceOf(JedisDataException.class));
    assertErrorType("WRONGTYPE");
  }

  @Test
  void nestedPipelineServerErrorDoesNotFailSuccessfulOuterPipeline() {
    jedis.lpush("nested-wrong-type", "value");
    testing.waitForTraces(1);
    testing.clearData();

    List<Object> nestedResults = new ArrayList<>();
    Pipeline outer = jedis.pipelined();
    outer.appendCommand(
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<String>() {
              @Override
              public String build(Object response) {
                Pipeline nested = jedis.pipelined();
                nested.get("nested-wrong-type");
                nestedResults.addAll(nested.syncAndReturnAll());
                return new String((byte[]) response, UTF_8);
              }
            }));
    outer.set("outer-pipeline", "value");

    assertThat(outer.syncAndReturnAll()).containsExactly("PONG", "OK");
    assertThat(nestedResults).hasSize(1);
    assertThat(nestedResults.get(0)).isInstanceOf(JedisDataException.class);
    assertThat((Throwable) nestedResults.get(0)).hasMessageStartingWith("WRONGTYPE");
    assertErrorType(null);
  }

  @Test
  void nestedTransactionServerErrorDoesNotFailSuccessfulOuterPipeline() {
    jedis.lpush("nested-wrong-type", "value");
    testing.waitForTraces(1);
    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    testing.clearData();

    List<Object> nestedResults = new ArrayList<>();
    Pipeline outer = jedis.pipelined();
    outer.appendCommand(
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<String>() {
              @Override
              public String build(Object response) {
                Transaction nested = nestedClient.multi();
                nested.get("nested-wrong-type");
                nestedResults.addAll(nested.exec());
                return new String((byte[]) response, UTF_8);
              }
            }));
    outer.set("outer-pipeline", "value");

    assertThat(outer.syncAndReturnAll()).containsExactly("PONG", "OK");
    assertThat(nestedResults).hasSize(1);
    assertThat(nestedResults.get(0)).isInstanceOf(JedisDataException.class);
    assertThat((Throwable) nestedResults.get(0)).hasMessageStartingWith("WRONGTYPE");
    assertErrorType(null);
  }

  @Test
  void nestedPipelineServerErrorDoesNotFailSuccessfulOuterTransaction()
      throws ReflectiveOperationException {
    jedis.lpush("nested-wrong-type", "value");
    testing.waitForTraces(1);
    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    testing.clearData();

    List<Object> nestedResults = new ArrayList<>();
    Transaction outer = jedis.multi();
    appendCommand(
        outer,
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<String>() {
              @Override
              public String build(Object response) {
                Pipeline nested = nestedClient.pipelined();
                nested.get("nested-wrong-type");
                nestedResults.addAll(nested.syncAndReturnAll());
                return new String((byte[]) response, UTF_8);
              }
            }));
    outer.set("outer-transaction", "value");

    assertThat(outer.exec()).containsExactly("PONG", "OK");
    assertThat(nestedResults).hasSize(1);
    assertThat(nestedResults.get(0)).isInstanceOf(JedisDataException.class);
    assertThat((Throwable) nestedResults.get(0)).hasMessageStartingWith("WRONGTYPE");
    assertErrorType(null);
  }

  private static <T> void appendCommand(Transaction transaction, CommandObject<T> command)
      throws ReflectiveOperationException {
    Method appendCommand = null;
    for (Class<?> type = transaction.getClass(); type != null; type = type.getSuperclass()) {
      try {
        appendCommand = type.getDeclaredMethod("appendCommand", CommandObject.class);
        break;
      } catch (NoSuchMethodException ignored) {
        // The method is declared on different transaction base classes across Jedis versions.
      }
    }
    if (appendCommand == null) {
      throw new NoSuchMethodException("appendCommand");
    }
    appendCommand.setAccessible(true);
    appendCommand.invoke(transaction, command);
  }

  @Test
  void nestedEmptyTransactionServerErrorDoesNotFailSuccessfulOuterPipeline() throws Exception {
    String username = "jedis-no-exec";
    String password = "password";
    assertThat(
            redisServer
                .execInContainer(
                    "redis-cli",
                    "ACL",
                    "SETUSER",
                    username,
                    "on",
                    ">" + password,
                    "allkeys",
                    "+@all",
                    "-exec")
                .getStdout()
                .trim())
        .isEqualTo("OK");
    cleanup.deferCleanup(
        () -> redisServer.execInContainer("redis-cli", "ACL", "DELUSER", username));
    JedisClientConfig clientConfig =
        DefaultJedisClientConfig.builder().user(username).password(password).build();
    Jedis nestedClient = new Jedis(new HostAndPort(host, port), clientConfig);
    cleanup.deferCleanup(nestedClient::disconnect);
    nestedClient.ping();
    testing.waitForTraces(2);
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-4.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .anySatisfy(
                            point ->
                                assertThat(point.getAttributes().get(DB_OPERATION_NAME))
                                    .isEqualTo("PING"))));
    testing.clearData();

    List<JedisDataException> nestedErrors = new ArrayList<>();
    Pipeline outer = jedis.pipelined();
    outer.appendCommand(
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<String>() {
              @Override
              public String build(Object response) {
                Transaction nested = nestedClient.multi();
                try {
                  nested.exec();
                } catch (JedisDataException error) {
                  nestedErrors.add(error);
                }
                return new String((byte[]) response, UTF_8);
              }
            }));
    outer.set("outer-pipeline", "value");

    assertThat(outer.syncAndReturnAll()).containsExactly("PONG", "OK");
    assertThat(nestedErrors).hasSize(1);
    assertThat(nestedErrors.get(0)).hasMessageContaining("NOPERM");
    assertErrorType(null);
  }

  @Test
  void nestedDiscardServerErrorDoesNotFailSuccessfulOuterPipeline() {
    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    testing.clearData();

    Pipeline outer = jedis.pipelined();
    outer.appendCommand(
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<String>() {
              @Override
              public String build(Object response) {
                Transaction nested = nestedClient.multi();
                String[] noKeys = {};
                nested.del(noKeys);
                nested.discard();
                return new String((byte[]) response, UTF_8);
              }
            }));
    outer.set("outer-pipeline", "value");

    assertThat(outer.syncAndReturnAll()).containsExactly("PONG", "OK");
    assertErrorType(null);
  }

  @Test
  void caughtNestedCommandServerErrorDoesNotFailSuccessfulOuterPipeline() {
    jedis.lpush("nested-wrong-type", "value");
    testing.waitForTraces(1);
    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    testing.clearData();

    List<JedisDataException> nestedErrors = new ArrayList<>();
    Pipeline outer = jedis.pipelined();
    outer.appendCommand(
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<String>() {
              @Override
              public String build(Object response) {
                try {
                  nestedClient.get("nested-wrong-type");
                } catch (JedisDataException error) {
                  nestedErrors.add(error);
                }
                return new String((byte[]) response, UTF_8);
              }
            }));
    outer.set("outer-pipeline", "value");

    assertThat(outer.syncAndReturnAll()).containsExactly("PONG", "OK");
    assertThat(nestedErrors).hasSize(1);
    assertThat(nestedErrors.get(0)).hasMessageStartingWith("WRONGTYPE");
    assertErrorType(null);
  }

  @Test
  void caughtDirectConnectionServerErrorDoesNotFailSuccessfulOuterPipeline() {
    jedis.lpush("nested-wrong-type", "value");
    testing.waitForTraces(1);
    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    testing.clearData();

    CommandObject<byte[]> nestedCommand =
        new CommandObject<>(
            new CommandArguments(Protocol.Command.GET).add("nested-wrong-type"),
            BuilderFactory.BINARY);
    Throwable independentError =
        catchThrowable(() -> nestedClient.getConnection().executeCommand(nestedCommand));
    assertThat(independentError).hasMessageStartingWith("WRONGTYPE");
    assertErrorType("WRONGTYPE");
    testing.clearData();

    List<JedisDataException> nestedErrors = new ArrayList<>();
    Pipeline outer = jedis.pipelined();
    outer.appendCommand(
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<String>() {
              @Override
              public String build(Object response) {
                try {
                  nestedClient.getConnection().executeCommand(nestedCommand);
                } catch (JedisDataException error) {
                  nestedErrors.add(error);
                }
                return new String((byte[]) response, UTF_8);
              }
            }));
    outer.set("outer-pipeline", "value");

    assertThat(outer.syncAndReturnAll()).containsExactly("PONG", "OK");
    assertThat(nestedErrors).hasSize(1);
    assertThat(nestedErrors.get(0)).hasMessageStartingWith("WRONGTYPE");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasAttributesSatisfying(equalTo(ERROR_TYPE, null))));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-4.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .allSatisfy(
                            point -> assertThat(point.getAttributes().get(ERROR_TYPE)).isNull())));

    testing.clearData();
    jedis.lpush("outer-wrong-type", "value");
    testing.waitForTraces(1);
    testing.clearData();
    List<JedisDataException> secondNestedErrors = new ArrayList<>();
    Pipeline failingOuter = jedis.pipelined();
    failingOuter.appendCommand(
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<String>() {
              @Override
              public String build(Object response) {
                try {
                  nestedClient.getConnection().executeCommand(nestedCommand);
                } catch (JedisDataException error) {
                  secondNestedErrors.add(error);
                }
                return new String((byte[]) response, UTF_8);
              }
            }));
    failingOuter.get("outer-wrong-type");

    List<Object> results = failingOuter.syncAndReturnAll();
    assertThat(secondNestedErrors).hasSize(1);
    assertThat(results).hasSize(2);
    assertThat(results.get(0)).isEqualTo("PONG");
    assertThat(results.get(1)).isInstanceOf(JedisDataException.class);
    assertErrorType("WRONGTYPE");
  }

  @Test
  void caughtSplitConnectionServerErrorDoesNotFailSuccessfulOuterPipeline() {
    jedis.lpush("nested-wrong-type", "value");
    testing.waitForTraces(1);
    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    nestedClient.ping();
    testing.waitForTraces(2);
    testing.clearData();

    List<JedisDataException> nestedErrors = new ArrayList<>();
    Pipeline outer = jedis.pipelined();
    outer.appendCommand(
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<String>() {
              @Override
              public String build(Object response) {
                try {
                  nestedClient
                      .getConnection()
                      .sendCommand(Protocol.Command.GET, "nested-wrong-type".getBytes(UTF_8));
                  nestedClient.getConnection().getOne();
                } catch (JedisDataException error) {
                  nestedErrors.add(error);
                }
                return new String((byte[]) response, UTF_8);
              }
            }));
    outer.set("outer-pipeline", "value");

    assertThat(outer.syncAndReturnAll()).containsExactly("PONG", "OK");
    assertThat(nestedErrors).hasSize(1);
    assertThat(nestedErrors.get(0)).hasMessageStartingWith("WRONGTYPE");

    testing.waitForTraces(1);
    assertThat(testing.spans())
        .hasSize(1)
        .allSatisfy(span -> assertThat(span.getAttributes().get(ERROR_TYPE)).isNull());
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-4.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .hasSize(1)
                        .allSatisfy(
                            point -> assertThat(point.getAttributes().get(ERROR_TYPE)).isNull())));
  }

  @ParameterizedTest
  @CsvSource({
    "false, false, false",
    "false, true, false",
    "true, false, false",
    "true, true, false",
    "false, false, true",
    "false, true, true",
    "true, false, true",
    "true, true, true"
  })
  void caughtRawConnectionServerErrorDoesNotFailOuterPipeline(
      boolean bulkReply, boolean outerFailure, boolean sameConnection) {
    jedis.lpush("nested-wrong-type", "value");
    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    nestedClient.ping();
    testing.waitForTraces(2);
    testing.clearData();

    List<Object> nestedErrors = new ArrayList<>();
    Pipeline outer = jedis.pipelined();
    outer.appendCommand(
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<String>() {
              @Override
              public String build(Object response) {
                Connection connection =
                    sameConnection ? jedis.getConnection() : nestedClient.getConnection();
                if (bulkReply) {
                  connection.sendCommand(Protocol.Command.GET, "nested-wrong-type".getBytes(UTF_8));
                  nestedErrors.add(catchThrowable(connection::getBulkReply));
                } else {
                  connection.sendCommand(Protocol.Command.PING);
                  connection.sendCommand(Protocol.Command.GET, "nested-wrong-type".getBytes(UTF_8));
                  List<Object> replies = connection.getMany(2);
                  assertThat(replies).hasSize(2);
                  assertThat(new String((byte[]) replies.get(0), UTF_8)).isEqualTo("PONG");
                  nestedErrors.add(replies.get(1));
                }
                return new String((byte[]) response, UTF_8);
              }
            }));
    if (outerFailure) {
      outer.get("nested-wrong-type");
    } else {
      outer.set("outer-pipeline", "value");
    }

    List<Object> results = outer.syncAndReturnAll();
    assertThat(results).hasSize(2);
    assertThat(results.get(0)).isEqualTo("PONG");
    if (outerFailure) {
      assertThat(results.get(1))
          .isInstanceOf(JedisDataException.class)
          .satisfies(error -> assertThat((Throwable) error).hasMessageStartingWith("WRONGTYPE"));
    } else {
      assertThat(results.get(1)).isEqualTo("OK");
    }
    assertThat(nestedErrors)
        .singleElement()
        .isInstanceOf(JedisDataException.class)
        .satisfies(error -> assertThat((Throwable) error).hasMessageStartingWith("WRONGTYPE"));
    String errorType = outerFailure ? "WRONGTYPE" : null;
    assertErrorType(errorType);
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-4.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .singleElement()
                        .satisfies(
                            point -> {
                              assertThat(point.getCount()).isEqualTo(1);
                              assertThat(point.getAttributes().get(ERROR_TYPE))
                                  .isEqualTo(errorType);
                            })));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void owningConnectionServerErrorFailsPipeline(boolean returnAll) {
    jedis.lpush("wrong-type", "value");
    testing.waitForTraces(1);
    testing.clearData();

    Pipeline pipeline = jedis.pipelined();
    Response<String> response = pipeline.get("wrong-type");
    pipeline.set("after-error", "value");

    if (returnAll) {
      List<Object> results = pipeline.syncAndReturnAll();
      assertThat(results).hasSize(2);
      assertThat(results.get(0))
          .isInstanceOf(JedisDataException.class)
          .satisfies(error -> assertThat((Throwable) error).hasMessageStartingWith("WRONGTYPE"));
      assertThat(results.get(1)).isEqualTo("OK");
    } else {
      pipeline.sync();
    }
    assertThatThrownBy(response::get).hasMessageStartingWith("WRONGTYPE");
    assertErrorType("WRONGTYPE");
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-4.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .singleElement()
                        .satisfies(
                            point -> {
                              assertThat(point.getCount()).isEqualTo(1);
                              assertThat(point.getAttributes().get(ERROR_TYPE))
                                  .isEqualTo("WRONGTYPE");
                            })));
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void escapingRawManyConnectionServerErrorFailsOuterPipeline(
      boolean throwError, boolean sameConnection) {
    jedis.lpush("nested-wrong-type", "value");
    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    nestedClient.ping();
    testing.waitForTraces(2);
    testing.clearData();

    Pipeline outer = jedis.pipelined();
    outer.appendCommand(
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<Object>() {
              @Override
              public Object build(Object response) {
                Connection connection =
                    sameConnection ? jedis.getConnection() : nestedClient.getConnection();
                connection.sendCommand(Protocol.Command.GET, "nested-wrong-type".getBytes(UTF_8));
                JedisDataException error = (JedisDataException) connection.getMany(1).get(0);
                if (throwError) {
                  throw error;
                }
                return error;
              }
            }));
    outer.set("outer-pipeline", "value");

    List<Object> results = outer.syncAndReturnAll();
    assertThat(results).hasSize(2);
    assertThat(results.get(0))
        .isInstanceOf(JedisDataException.class)
        .satisfies(error -> assertThat((Throwable) error).hasMessageStartingWith("WRONGTYPE"));
    assertThat(results.get(1)).isEqualTo("OK");
    assertErrorType("WRONGTYPE");
  }

  @Test
  void uncaughtNestedCommandServerErrorFailsOuterPipeline() {
    jedis.lpush("nested-wrong-type", "value");
    testing.waitForTraces(1);
    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    testing.clearData();

    Pipeline outer = jedis.pipelined();
    outer.appendCommand(
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<String>() {
              @Override
              public String build(Object response) {
                return nestedClient.get("nested-wrong-type");
              }
            }));
    outer.set("outer-pipeline", "value");

    List<Object> results = outer.syncAndReturnAll();
    assertThat(results).hasSize(2);
    assertThat(results.get(0))
        .isInstanceOf(JedisDataException.class)
        .satisfies(error -> assertThat((Throwable) error).hasMessageStartingWith("WRONGTYPE"));
    assertThat(results.get(1)).isEqualTo("OK");
    assertErrorType("WRONGTYPE");
  }

  @Test
  void deferredNestedPipelineServerErrorFailsOuterPipeline() {
    jedis.lpush("nested-wrong-type", "value");
    testing.waitForTraces(1);
    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    testing.clearData();

    Pipeline outer = jedis.pipelined();
    outer.appendCommand(
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<String>() {
              @Override
              public String build(Object response) {
                Pipeline nested = nestedClient.pipelined();
                Response<String> deferred = nested.get("nested-wrong-type");
                nested.sync();
                return deferred.get();
              }
            }));
    outer.set("outer-pipeline", "value");

    List<Object> results = outer.syncAndReturnAll();
    assertThat(results).hasSize(2);
    assertThat(results.get(0))
        .isInstanceOf(JedisDataException.class)
        .satisfies(error -> assertThat((Throwable) error).hasMessageStartingWith("WRONGTYPE"));
    assertThat(results.get(1)).isEqualTo("OK");
    assertErrorType("WRONGTYPE");
  }

  @Test
  void uncaughtNestedCommandServerErrorFailsOuterTransaction() throws ReflectiveOperationException {
    jedis.lpush("nested-wrong-type", "value");
    testing.waitForTraces(1);
    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    testing.clearData();

    Transaction outer = jedis.multi();
    appendCommand(
        outer,
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<String>() {
              @Override
              public String build(Object response) {
                return nestedClient.get("nested-wrong-type");
              }
            }));
    outer.set("outer-transaction", "value");

    List<Object> results = outer.exec();
    assertThat(results).hasSize(2);
    assertThat(results.get(0))
        .isInstanceOf(JedisDataException.class)
        .satisfies(error -> assertThat((Throwable) error).hasMessageStartingWith("WRONGTYPE"));
    assertThat(results.get(1)).isEqualTo("OK");
    assertErrorType("WRONGTYPE");
  }

  @ParameterizedTest
  @CsvSource({
    "false, false, false",
    "false, true, false",
    "true, false, false",
    "true, true, false",
    "false, false, true",
    "false, true, true",
    "true, false, true",
    "true, true, true"
  })
  void caughtRawConnectionServerErrorDoesNotFailOuterTransaction(
      boolean bulkReply, boolean outerFailure, boolean sameConnection)
      throws ReflectiveOperationException {
    jedis.lpush("nested-wrong-type", "value");
    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    nestedClient.ping();
    testing.waitForTraces(2);
    testing.clearData();

    List<Object> nestedErrors = new ArrayList<>();
    Transaction outer = jedis.multi();
    appendCommand(
        outer,
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<String>() {
              @Override
              public String build(Object response) {
                Connection connection =
                    sameConnection ? jedis.getConnection() : nestedClient.getConnection();
                if (bulkReply) {
                  connection.sendCommand(Protocol.Command.GET, "nested-wrong-type".getBytes(UTF_8));
                  nestedErrors.add(catchThrowable(connection::getBulkReply));
                } else {
                  connection.sendCommand(Protocol.Command.PING);
                  connection.sendCommand(Protocol.Command.GET, "nested-wrong-type".getBytes(UTF_8));
                  List<Object> replies = connection.getMany(2);
                  assertThat(replies).hasSize(2);
                  assertThat(new String((byte[]) replies.get(0), UTF_8)).isEqualTo("PONG");
                  nestedErrors.add(replies.get(1));
                }
                return new String((byte[]) response, UTF_8);
              }
            }));
    if (outerFailure) {
      outer.get("nested-wrong-type");
    } else {
      outer.set("outer-transaction", "value");
    }

    List<Object> results = outer.exec();
    assertThat(results).hasSize(2);
    assertThat(results.get(0)).isEqualTo("PONG");
    if (outerFailure) {
      assertThat(results.get(1))
          .isInstanceOf(JedisDataException.class)
          .satisfies(error -> assertThat((Throwable) error).hasMessageStartingWith("WRONGTYPE"));
    } else {
      assertThat(results.get(1)).isEqualTo("OK");
    }
    assertThat(nestedErrors)
        .singleElement()
        .isInstanceOf(JedisDataException.class)
        .satisfies(error -> assertThat((Throwable) error).hasMessageStartingWith("WRONGTYPE"));
    String errorType = outerFailure ? "WRONGTYPE" : null;
    assertErrorType(errorType);
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-4.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .singleElement()
                        .satisfies(
                            point -> {
                              assertThat(point.getCount()).isEqualTo(1);
                              assertThat(point.getAttributes().get(ERROR_TYPE))
                                  .isEqualTo(errorType);
                            })));
    jedis.resetState();
    testing.clearData();
    assertThat(jedis.set("after-transaction", "value")).isEqualTo("OK");
    assertErrorType(null);
  }

  @ParameterizedTest
  @CsvSource({"false, false", "false, true", "true, false", "true, true"})
  void escapingRawManyConnectionServerErrorFailsOuterTransaction(
      boolean throwError, boolean sameConnection) throws ReflectiveOperationException {
    jedis.lpush("nested-wrong-type", "value");
    Jedis nestedClient = new Jedis(host, port);
    cleanup.deferCleanup(nestedClient::disconnect);
    nestedClient.ping();
    testing.waitForTraces(2);
    testing.clearData();

    Transaction outer = jedis.multi();
    appendCommand(
        outer,
        new CommandObject<>(
            new CommandArguments(Protocol.Command.PING),
            new Builder<Object>() {
              @Override
              public Object build(Object response) {
                Connection connection =
                    sameConnection ? jedis.getConnection() : nestedClient.getConnection();
                connection.sendCommand(Protocol.Command.GET, "nested-wrong-type".getBytes(UTF_8));
                JedisDataException error = (JedisDataException) connection.getMany(1).get(0);
                if (throwError) {
                  throw error;
                }
                return error;
              }
            }));
    outer.set("outer-transaction", "value");

    List<Object> results = outer.exec();
    assertThat(results).hasSize(2);
    assertThat(results.get(0))
        .isInstanceOf(JedisDataException.class)
        .satisfies(error -> assertThat((Throwable) error).hasMessageStartingWith("WRONGTYPE"));
    assertThat(results.get(1)).isEqualTo("OK");
    assertErrorType("WRONGTYPE");

    jedis.resetState();
    testing.clearData();
    assertThat(jedis.set("after-transaction", "value")).isEqualTo("OK");
    assertErrorType(null);
  }

  @Test
  void terminalTransactionServerFailureWinsOverQueuedError() {
    Jedis client = new Jedis(host, port);
    cleanup.deferCleanup(client::disconnect);
    Transaction transaction = client.multi();
    String[] noKeys = {};
    transaction.del(noKeys);
    testing.clearData();

    Throwable error = catchThrowable(transaction::exec);
    assertThat(error).isInstanceOf(JedisDataException.class);
    assertThat(error.getMessage()).startsWith("EXECABORT");
    assertErrorType(
        error.getClass() == JedisDataException.class ? "EXECABORT" : error.getClass().getName());

    testing.clearData();
    jedis.set("after-error", "value");
    assertErrorType(null);
  }

  @Test
  void terminalPipelineConnectionFailureWinsOverServerReply() {
    Jedis client = new Jedis(host, port);
    cleanup.deferCleanup(client::disconnect);
    client.lpush("wrong-type", "value");
    testing.waitForTraces(1);

    Pipeline pipeline = client.pipelined();
    pipeline.get("wrong-type");
    client.getClient().sendCommand(() -> "QUIT".getBytes(UTF_8));
    pipeline.get("after-quit");
    pipeline.get("after-quit-again");
    testing.clearData();

    Throwable error = catchThrowable(pipeline::syncAndReturnAll);
    assertThat(error).isInstanceOf(JedisConnectionException.class);
    assertErrorType(error.getClass().getName());

    testing.clearData();
    jedis.set("after-error", "value");
    assertErrorType(null);
  }

  private static void assertErrorType(String errorType) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasAttributesSatisfying(equalTo(ERROR_TYPE, errorType))));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-4.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .anySatisfy(
                            point ->
                                assertThat(point.getAttributes().get(ERROR_TYPE))
                                    .isEqualTo(errorType))));
  }

  @Test
  void mappedCommandUsesConfiguredTarget() {
    String configuredHost = "redis.internal";
    int configuredPort = 6380;
    try (Jedis mapped =
        new Jedis(
            new HostAndPort(configuredHost, configuredPort),
            DefaultJedisClientConfig.builder()
                .hostAndPortMapper(ignored -> new HostAndPort(host, port))
                .build())) {
      testing.clearData();
      mapped.set("mapped", "value");
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + configuredHost + ":" + configuredPort)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET mapped ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, configuredHost),
                            equalTo(SERVER_PORT, configuredPort),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));

    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-4.0",
        metric ->
            metric
                .hasName("db.client.operation.duration")
                .hasHistogramSatisfying(
                    histogram ->
                        histogram.hasPointsSatisfying(
                            point ->
                                point
                                    .hasAttribute(DB_OPERATION_NAME, "SET")
                                    .hasAttribute(SERVER_ADDRESS, configuredHost)
                                    .hasAttribute(SERVER_PORT, (long) configuredPort))));
  }

  @Test
  void shardedCommandUsesConfiguredTargets() throws ReflectiveOperationException {
    Class<? extends UnifiedJedis> jedisShardingClass;
    try {
      jedisShardingClass =
          Class.forName("redis.clients.jedis.JedisSharding").asSubclass(UnifiedJedis.class);
    } catch (ClassNotFoundException ignored) {
      assertThat(Boolean.getBoolean("testLatestDeps")).isTrue();
      return;
    }

    boolean jedis51OrLater = Boolean.getBoolean("testJedis51OrLater");
    String firstShard = "redis-one.internal";
    int firstShardPort = 6380;
    String secondShard = "redis-two.internal";
    int secondShardPort = 6381;
    DefaultJedisClientConfig clientConfig =
        DefaultJedisClientConfig.builder()
            .hostAndPortMapper(ignored -> new HostAndPort(host, port))
            .build();
    String configuredTargets =
        firstShard + ":" + firstShardPort + "," + secondShard + ":" + secondShardPort;
    try (UnifiedJedis sharded =
        createShardedClient(
            asList(
                new HostAndPort(firstShard, firstShardPort),
                new HostAndPort(secondShard, secondShardPort)),
            clientConfig,
            jedisShardingClass)) {
      sharded.set("sharded", "warmup");
      testing.waitForTraces(jedis51OrLater ? 3 : 1);
      testing.clearData();
      sharded.set("sharded", "value");

      // Assert before close() can add the pre-5.1 QUIT trace.
      testing.waitAndAssertTraces(
          trace ->
              trace.hasSpansSatisfyingExactly(
                  span ->
                      span.hasName("SET " + configuredTargets)
                          .hasKind(SpanKind.CLIENT)
                          .hasAttributesSatisfyingExactly(
                              equalTo(DB_SYSTEM_NAME, REDIS),
                              equalTo(DB_QUERY_TEXT, "SET sharded ?"),
                              equalTo(DB_OPERATION_NAME, "SET"),
                              equalTo(DB_NAMESPACE, "0"),
                              equalTo(SERVER_ADDRESS, configuredTargets),
                              equalTo(NETWORK_PEER_PORT, port),
                              equalTo(NETWORK_PEER_ADDRESS, ip))));
    }
    if (!jedis51OrLater) {
      // The asserted SET trace remains captured; close() adds a second trace for QUIT.
      testing.waitForTraces(2);
    }
  }

  private static UnifiedJedis createShardedClient(
      List<HostAndPort> shards,
      JedisClientConfig clientConfig,
      Class<? extends UnifiedJedis> jedisShardingClass)
      throws ReflectiveOperationException {
    return jedisShardingClass
        .getConstructor(List.class, JedisClientConfig.class)
        .newInstance(shards, clientConfig);
  }

  private static void readRedisCommand(InputStream input) throws IOException {
    String arrayHeader = readRedisLine(input);
    if (!arrayHeader.startsWith("*")) {
      throw new IOException("Expected a RESP array");
    }

    int argumentCount = Integer.parseInt(arrayHeader.substring(1));
    for (int i = 0; i < argumentCount; i++) {
      String bulkHeader = readRedisLine(input);
      if (!bulkHeader.startsWith("$")) {
        throw new IOException("Expected a RESP bulk string");
      }

      int remaining = Integer.parseInt(bulkHeader.substring(1)) + 2;
      while (remaining-- > 0) {
        if (input.read() == -1) {
          throw new EOFException();
        }
      }
    }
  }

  private static String readRedisLine(InputStream input) throws IOException {
    StringBuilder line = new StringBuilder();
    while (true) {
      int value = input.read();
      if (value == -1) {
        throw new EOFException();
      }
      if (value == '\r') {
        if (input.read() != '\n') {
          throw new IOException("Expected CRLF");
        }
        return line.toString();
      }
      line.append((char) value);
    }
  }

  @Test
  void defaultSocketFactoryUsesDefaultConfiguredTarget() {
    DefaultJedisSocketFactory delegate = new DefaultJedisSocketFactory(new HostAndPort(host, port));
    DefaultJedisSocketFactory socketFactory =
        new DefaultJedisSocketFactory() {
          @Override
          public Socket createSocket() {
            return delegate.createSocket();
          }
        };
    try (Jedis direct = new Jedis(socketFactory)) {
      testing.clearData();
      direct.set("default-factory", "value");
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET 127.0.0.1")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET default-factory ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @Test
  void configOnlySocketFactoryUsesDefaultConfiguredTarget() {
    DefaultJedisClientConfig clientConfig =
        DefaultJedisClientConfig.builder()
            .hostAndPortMapper(ignored -> new HostAndPort(host, port))
            .build();
    try (Jedis mapped = new Jedis(new DefaultJedisSocketFactory(clientConfig), clientConfig)) {
      testing.clearData();
      mapped.set("mapped-default", "value");
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET 127.0.0.1")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET mapped-default ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, "127.0.0.1"),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @Test
  void customSocketFactoryFallsBackToNetworkPeer() {
    DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder().build();
    DefaultJedisSocketFactory delegate =
        new DefaultJedisSocketFactory(new HostAndPort(host, port), clientConfig);
    JedisSocketFactory socketFactory =
        (JedisSocketFactory)
            Proxy.newProxyInstance(
                JedisSocketFactory.class.getClassLoader(),
                new Class<?>[] {JedisSocketFactory.class},
                (proxy, method, args) -> {
                  try {
                    return method.invoke(delegate, args);
                  } catch (InvocationTargetException e) {
                    throw e.getCause();
                  }
                });

    try (Jedis custom = new Jedis(socketFactory, clientConfig)) {
      testing.clearData();
      custom.set("custom", "value");
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET custom ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @Test
  void pooledCommand() {
    JedisPool pool = new JedisPool(host, port);
    cleanup.deferCleanup(pool);
    try (Jedis pooled = pool.getResource()) {
      testing.clearData();
      pooled.set("pooled", "value");
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET pooled ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @Test
  void getCommand() {
    jedis.set("foo", "bar");
    String value = jedis.get("foo");

    assertThat(value).isEqualTo("bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET foo ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "GET foo"),
                            equalTo(DB_OPERATION_NAME, "GET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @Test
  void commandWithNoArguments() {
    jedis.set("foo", "bar");
    String value = jedis.randomKey();

    assertThat(value).isEqualTo("foo");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET foo ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("RANDOMKEY " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "RANDOMKEY"),
                            equalTo(DB_OPERATION_NAME, "RANDOMKEY"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @Test
  void selectCommandDoesNotChangeEstablishedDatabaseIndex() {
    jedis.select(1);
    jedis.set("foo", "bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SELECT 1"),
                            equalTo(DB_OPERATION_NAME, "SELECT"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET foo ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @Test
  void configuredDatabaseIndex() {
    try (Jedis databaseOne =
        new Jedis(
            new HostAndPort(host, port), DefaultJedisClientConfig.builder().database(1).build())) {
      testing.clearData();
      databaseOne.set("foo", "bar");
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, "SET foo ?"),
                            equalTo(DB_OPERATION_NAME, "SET"),
                            equalTo(DB_NAMESPACE, "1"),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @ParameterizedTest
  @MethodSource("batchScenarios")
  void pipelineCommand(BatchScenario<Pipeline> scenario) {
    Pipeline pipeline = jedis.pipelined();
    scenario.commands.accept(pipeline);
    pipeline.sync();

    if (scenario.operationName == null) {
      assertThat(testing.spans()).isEmpty();
      return;
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(scenario.operationName + " " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, scenario.queryText),
                            equalTo(DB_OPERATION_NAME, scenario.operationName),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_OPERATION_BATCH_SIZE, scenario.batchSize),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  private static Stream<Arguments> batchScenarios() {
    return Stream.of(
        // no span is created for empty pipelines
        argumentSet("empty", BatchScenario.<Pipeline>builder().build()),
        argumentSet(
            "single",
            BatchScenario.<Pipeline>builder()
                .addCommand(pipeline -> pipeline.set("batch1", "v1"))
                .operationName("SET")
                .queryText("SET batch1 ?")
                .build()),
        argumentSet(
            "twoSameOperation",
            BatchScenario.<Pipeline>builder()
                .addCommand(pipeline -> pipeline.set("batch1", "v1"))
                .addCommand(pipeline -> pipeline.set("batch2", "v2"))
                .operationName("PIPELINE SET")
                .queryText("SET batch1 ?; SET batch2 ?")
                .batchSize(2)
                .build()),
        argumentSet(
            "twoDifferentOperations",
            BatchScenario.<Pipeline>builder()
                .addCommand(pipeline -> pipeline.set("batch1", "v1"))
                .addCommand(pipeline -> pipeline.get("batch1"))
                .operationName("PIPELINE")
                .queryText("SET batch1 ?; GET batch1")
                .batchSize(2)
                .build()));
  }

  @ParameterizedTest
  @MethodSource("transactionScenarios")
  void transactionCommand(BatchScenario<Transaction> scenario) {
    // A MULTI/EXEC transaction is reported as a single batch span (db.operation.name "MULTI"),
    // mirroring how a pipeline is reported as "PIPELINE". The framing MULTI/EXEC commands and the
    // individual queued commands do not get their own spans.
    Transaction transaction = jedis.multi();
    scenario.commands.accept(transaction);
    transaction.exec();

    if (scenario.operationName == null) {
      assertThat(testing.spans()).isEmpty();
      return;
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(scenario.operationName + " " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_QUERY_TEXT, scenario.queryText),
                            equalTo(DB_OPERATION_NAME, scenario.operationName),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_OPERATION_BATCH_SIZE, scenario.batchSize),
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip))));
  }

  @Test
  void transactionDiscardEmitsNoSpans() {
    // A discarded transaction is abandoned: its queued commands are dropped and the framing
    // MULTI/DISCARD commands do not produce any spans.
    Transaction transaction = jedis.multi();
    transaction.set("tx1", "v1");
    transaction.get("tx1");
    transaction.discard();

    assertThat(testing.spans()).isEmpty();
  }

  private static Stream<Arguments> transactionScenarios() {
    return Stream.of(
        // no span is created for empty transactions
        argumentSet("empty", BatchScenario.<Transaction>builder().build()),
        argumentSet(
            "single",
            BatchScenario.<Transaction>builder()
                .addCommand(transaction -> transaction.set("tx1", "v1"))
                .operationName("SET")
                .queryText("SET tx1 ?")
                .build()),
        argumentSet(
            "twoSameOperation",
            BatchScenario.<Transaction>builder()
                .addCommand(transaction -> transaction.set("tx1", "v1"))
                .addCommand(transaction -> transaction.set("tx2", "v2"))
                .operationName("MULTI SET")
                .queryText("SET tx1 ?; SET tx2 ?")
                .batchSize(2)
                .build()),
        argumentSet(
            "twoDifferentOperations",
            BatchScenario.<Transaction>builder()
                .addCommand(transaction -> transaction.set("tx1", "v1"))
                .addCommand(transaction -> transaction.get("tx1"))
                .operationName("MULTI")
                .queryText("SET tx1 ?; GET tx1")
                .batchSize(2)
                .build()));
  }

  private static class BatchScenario<T> {
    private final Consumer<T> commands;
    private final String operationName;
    private final String queryText;
    private final Long batchSize;

    private BatchScenario(Builder<T> builder) {
      this.commands = builder.commands;
      this.operationName = builder.operationName;
      this.queryText = builder.queryText;
      this.batchSize = builder.batchSize;
    }

    private static <T> Builder<T> builder() {
      return new Builder<>();
    }

    private static class Builder<T> {
      private Consumer<T> commands = command -> {};
      private String operationName;
      private String queryText;
      private Long batchSize;

      private Builder<T> addCommand(Consumer<T> command) {
        this.commands = this.commands.andThen(command);
        return this;
      }

      private Builder<T> operationName(String operationName) {
        this.operationName = operationName;
        return this;
      }

      private Builder<T> queryText(String queryText) {
        this.queryText = queryText;
        return this;
      }

      private Builder<T> batchSize(long batchSize) {
        this.batchSize = batchSize;
        return this;
      }

      private BatchScenario<T> build() {
        return new BatchScenario<>(this);
      }
    }
  }
}
