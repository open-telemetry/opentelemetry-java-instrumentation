/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;

import com.google.common.collect.ImmutableMap;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.masterslave.MasterSlave;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@SuppressWarnings("deprecation") // using deprecated semconv
class LettuceSyncClientTest extends AbstractLettuceClientTest {
  private int incorrectPort;
  private String dbUriNonExistent;

  private static final ImmutableMap<String, String> testHashMap =
      ImmutableMap.of(
          "firstname", "John",
          "lastname", "Doe",
          "age", "53");

  private RedisCommands<String, String> syncCommands;

  @BeforeAll
  void setUp() throws UnknownHostException {
    redisServer.start();
    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    incorrectPort = PortUtils.findOpenPort();
    dbUriNonExistent = "redis://" + host + ":" + incorrectPort + "/" + DB_INDEX;

    redisClient = RedisClient.create(embeddedDbUri);
    redisClient.setOptions(CLIENT_OPTIONS);

    connection = redisClient.connect();
    syncCommands = connection.sync();

    syncCommands.set("TESTKEY", "TESTVAL");
    syncCommands.hmset("TESTHM", testHashMap);

    testing.waitForTraces(connectionTelemetryEnabled() ? 3 : 2);
  }

  @AfterAll
  void cleanUp() {
    connection.close();
    shutdown(redisClient);
    redisServer.stop();
  }

  @Test
  @EnabledIfSystemProperty(
      named = "otel.instrumentation.lettuce.connection-telemetry.enabled",
      matches = "true")
  void testConnect() {
    RedisClient testConnectionClient = RedisClient.create(embeddedDbUri);
    testConnectionClient.setOptions(CLIENT_OPTIONS);

    StatefulRedisConnection<String, String> testConnection = testConnectionClient.connect();
    cleanup.deferCleanup(() -> shutdown(testConnectionClient));
    cleanup.deferCleanup(testConnection);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("CONNECT")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(maybeStablePeerService(), "test-peer-service"))));
  }

  @Test
  @EnabledIfSystemProperty(
      named = "otel.instrumentation.lettuce.connection-telemetry.enabled",
      matches = "true")
  void testConnectException() {
    RedisClient testConnectionClient = RedisClient.create(dbUriNonExistent);
    testConnectionClient.setOptions(CLIENT_OPTIONS);

    Exception exception = catchException(testConnectionClient::connect);

    assertThat(exception).isInstanceOf(RedisConnectionException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("CONNECT")
                        .hasKind(SpanKind.CLIENT)
                        .hasStatus(StatusData.error())
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, incorrectPort),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(maybeStablePeerService(), "test-peer-service"))
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            EXCEPTION_TYPE,
                                            "io.netty.channel.AbstractChannel.AnnotatedConnectException"),
                                        satisfies(
                                            EXCEPTION_MESSAGE,
                                            val ->
                                                val.matches(
                                                    expectedConnectionRefusedMessagePattern(
                                                        incorrectPort))),
                                        satisfies(EXCEPTION_STACKTRACE, val -> val.isNotNull())))));
  }

  @Test
  void testSetCommand() {
    String res = syncCommands.set("TESTSETKEY", "TESTSETVAL");
    assertThat(res).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "SET TESTSETKEY ?"),
                            equalTo(DB_OPERATION_NAME, "SET"))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.lettuce-5.0",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_NAMESPACE,
        SERVER_ADDRESS,
        SERVER_PORT,
        NETWORK_PEER_ADDRESS,
        NETWORK_PEER_PORT);
  }

  @Test
  @DisabledIfSystemProperty(
      named = "otel.instrumentation.lettuce.connection-telemetry.enabled",
      matches = "true")
  void testMasterSlaveCommandsAndBatchUseConfiguredUris() throws Exception {
    List<RedisURI> redisUris =
        asList(RedisURI.create(embeddedDbUri), RedisURI.create(embeddedDbUri));
    String configuredTarget = host + ":" + port + "," + host + ":" + port;
    StatefulRedisConnection<String, String> masterSlaveConnection = connectMasterReplica(redisUris);
    cleanup.deferCleanup(masterSlaveConnection);

    testing.waitForTraces(
        masterSlaveConnection.getClass().getName().contains(".masterreplica.") ? 5 : 4);
    testing.clearData();

    assertThat(masterSlaveConnection.sync().set("MASTER_SLAVE_COMMAND_KEY", "value"))
        .isEqualTo("OK");

    masterSlaveConnection.setAutoFlushCommands(false);
    cleanup.deferCleanup(() -> masterSlaveConnection.setAutoFlushCommands(true));
    RedisAsyncCommands<String, String> asyncCommands = masterSlaveConnection.async();
    RedisFuture<String> first = asyncCommands.set("MASTER_SLAVE_BATCH_KEY_1", "value");
    RedisFuture<String> second = asyncCommands.set("MASTER_SLAVE_BATCH_KEY_2", "value");
    masterSlaveConnection.flushCommands();
    assertThat(first.get(10, SECONDS)).isEqualTo("OK");
    assertThat(second.get(10, SECONDS)).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + configuredTarget)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, configuredTarget),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "SET MASTER_SLAVE_COMMAND_KEY ?"),
                            equalTo(DB_OPERATION_NAME, "SET"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("PIPELINE SET " + configuredTarget)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, configuredTarget),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(
                                DB_QUERY_TEXT,
                                "SET MASTER_SLAVE_BATCH_KEY_1 ?; SET MASTER_SLAVE_BATCH_KEY_2 ?"),
                            equalTo(DB_OPERATION_NAME, "PIPELINE SET"),
                            equalTo(DB_OPERATION_BATCH_SIZE, Long.valueOf(2)))));
  }

  @SuppressWarnings("unchecked")
  private StatefulRedisConnection<String, String> connectMasterReplica(List<RedisURI> redisUris)
      throws Exception {
    try {
      // This shared test source compiles against 5.0, before the MasterReplica iterable API.
      Class<?> masterReplica =
          Class.forName(
              "io.lettuce.core.masterreplica.MasterReplica", false, getClass().getClassLoader());
      Method connectAsync =
          masterReplica.getMethod(
              "connectAsync", RedisClient.class, RedisCodec.class, Iterable.class);
      CompletableFuture<StatefulRedisConnection<String, String>> connection =
          (CompletableFuture<StatefulRedisConnection<String, String>>)
              connectAsync.invoke(null, redisClient, StringCodec.UTF8, redisUris);
      return connection.get(10, SECONDS);
    } catch (ClassNotFoundException | NoSuchMethodException ignored) {
      return MasterSlave.connect(redisClient, StringCodec.UTF8, redisUris);
    }
  }

  @Test
  void testUriMutationDoesNotChangeEstablishedAttributes() {
    RedisURI redisUri = RedisURI.create(embeddedDbUri);
    RedisClient testClient = RedisClient.create(redisUri);
    testClient.setOptions(CLIENT_OPTIONS);
    cleanup.deferCleanup(() -> shutdown(testClient));
    StatefulRedisConnection<String, String> testConnection = testClient.connect();
    cleanup.deferCleanup(testConnection);

    if (connectionTelemetryEnabled()) {
      testing.waitForTraces(1);
    }
    testing.clearData();

    redisUri.setHost("example.com");
    redisUri.setPort(1234);
    redisUri.setDatabase(1);
    assertThat(testConnection.sync().set("URI_MUTATION_TEST_KEY", "URI_MUTATION_TEST_VALUE"))
        .isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "SET URI_MUTATION_TEST_KEY ?"),
                            equalTo(DB_OPERATION_NAME, "SET"))));
  }

  @Test
  void testGetCommand() {
    String res = syncCommands.get("TESTKEY");
    assertThat(res).isEqualTo("TESTVAL");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "GET TESTKEY"),
                            equalTo(DB_OPERATION_NAME, "GET"))));
  }

  @Test
  void testGetNonExistentKeyCommand() {
    String res = syncCommands.get("NON_EXISTENT_KEY");
    assertThat(res).isNull();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "GET NON_EXISTENT_KEY"),
                            equalTo(DB_OPERATION_NAME, "GET"))));
  }

  @Test
  void testCommandWithNoArguments() {
    String res = syncCommands.randomkey();
    assertThat(res).isNotNull();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("RANDOMKEY " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "RANDOMKEY"),
                            equalTo(DB_OPERATION_NAME, "RANDOMKEY"))));
  }

  @Test
  void testListCommand() {
    long res = syncCommands.lpush("TESTLIST", "TESTLIST ELEMENT");
    assertThat(res).isEqualTo(1);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("LPUSH " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "LPUSH TESTLIST ?"),
                            equalTo(DB_OPERATION_NAME, "LPUSH"))));
  }

  @Test
  void testHashSetCommand() {
    String res = syncCommands.hmset("user", testHashMap);
    assertThat(res).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("HMSET " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "HMSET user firstname ? lastname ? age ?"),
                            equalTo(DB_OPERATION_NAME, "HMSET"))));
  }

  @Test
  void testHashGetallCommand() {
    Map<String, String> res = syncCommands.hgetall("TESTHM");
    assertThat(res).isEqualTo(testHashMap);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("HGETALL " + host + ":" + port)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(NETWORK_PEER_ADDRESS, ip),
                            equalTo(NETWORK_PEER_PORT, Long.valueOf(port)),
                            equalTo(DB_SYSTEM_NAME, REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(DB_QUERY_TEXT, "HGETALL TESTHM"),
                            equalTo(DB_OPERATION_NAME, "HGETALL"))));
  }

  @Test
  void testDebugSegfaultCommandWithNoArgumentShouldProduceSpan() {
    withIsolatedContainer(
        (connection, port) -> {
          RedisCommands<String, String> commands = connection.sync();
          commands.debugSegfault();

          testing.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("DEBUG " + host + ":" + port)
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(SERVER_ADDRESS, host),
                                  equalTo(SERVER_PORT, port),
                                  equalTo(DB_SYSTEM_NAME, REDIS),
                                  equalTo(DB_NAMESPACE, "0"),
                                  equalTo(DB_QUERY_TEXT, "DEBUG SEGFAULT"),
                                  equalTo(DB_OPERATION_NAME, "DEBUG"))));
        });
  }

  @Test
  void testShutdownCommandShouldProduceSpan() {
    withIsolatedContainer(
        (connection, port) -> {
          RedisCommands<String, String> commands = connection.sync();
          commands.shutdown(false);

          testing.waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("SHUTDOWN " + host + ":" + port)
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(SERVER_ADDRESS, host),
                                  equalTo(SERVER_PORT, port),
                                  equalTo(DB_SYSTEM_NAME, REDIS),
                                  equalTo(DB_NAMESPACE, "0"),
                                  equalTo(DB_QUERY_TEXT, "SHUTDOWN NOSAVE"),
                                  equalTo(DB_OPERATION_NAME, "SHUTDOWN"))));
        });
  }
}
