/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.DbClientMetricsTestUtil.assertDurationMetric;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.instrumentation.testing.junit.service.SemconvServiceStabilityUtil.maybeStablePeerService;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_NAMESPACE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_BATCH_SIZE;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_REDIS_DATABASE_INDEX;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.common.collect.ImmutableMap;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.sync.RedisCommands;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.masterslave.MasterSlave;
import io.lettuce.core.masterslave.StatefulRedisMasterSlaveConnection;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
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
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(DB_REDIS_DATABASE_INDEX, null),
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
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(DB_REDIS_DATABASE_INDEX, null),
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
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "SET TESTSETKEY ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"))));

    assertDurationMetric(
        testing,
        "io.opentelemetry.lettuce-5.0",
        DB_SYSTEM_NAME,
        DB_OPERATION_NAME,
        DB_NAMESPACE,
        SERVER_ADDRESS,
        SERVER_PORT);
  }

  @Test
  @DisabledIfSystemProperty(
      named = "otel.instrumentation.lettuce.connection-telemetry.enabled",
      matches = "true")
  void testMasterSlaveCommandsAndBatchUseConfiguredUris() throws Exception {
    assumeTrue(emitStableDatabaseSemconv());

    List<RedisURI> redisUris =
        asList(RedisURI.create(embeddedDbUri), RedisURI.create(embeddedDbUri));
    String configuredTarget = host + ":" + port + "," + host + ":" + port;
    StatefulRedisMasterSlaveConnection<String, String> masterSlaveConnection =
        MasterSlave.connect(redisClient, StringCodec.UTF8, redisUris);
    cleanup.deferCleanup(masterSlaveConnection);

    testing.waitForTraces(2);
    testing.clearData();

    assertThat(masterSlaveConnection.sync().set("MASTER_SLAVE_COMMAND_KEY", "value"))
        .isEqualTo("OK");

    masterSlaveConnection.setAutoFlushCommands(false);
    RedisAsyncCommands<String, String> asyncCommands = masterSlaveConnection.async();
    RedisFuture<String> first = asyncCommands.set("MASTER_SLAVE_BATCH_KEY_1", "value");
    RedisFuture<String> second = asyncCommands.set("MASTER_SLAVE_BATCH_KEY_2", "value");
    masterSlaveConnection.flushCommands();
    masterSlaveConnection.setAutoFlushCommands(true);
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
                            equalTo(SERVER_PORT, null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(maybeStable(DB_STATEMENT), "SET MASTER_SLAVE_COMMAND_KEY ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("PIPELINE SET " + configuredTarget)
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, configuredTarget),
                            equalTo(SERVER_PORT, null),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, "0"),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "SET MASTER_SLAVE_BATCH_KEY_1 ?; SET MASTER_SLAVE_BATCH_KEY_2 ?"),
                            equalTo(maybeStable(DB_OPERATION), "PIPELINE SET"),
                            equalTo(DB_OPERATION_BATCH_SIZE, 2))));
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
                    span.hasName(emitStableDatabaseSemconv() ? "SET " + host + ":" + port : "SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "SET URI_MUTATION_TEST_KEY ?"),
                            equalTo(maybeStable(DB_OPERATION), "SET"))));
  }

  @Test
  void testGetCommand() {
    String res = syncCommands.get("TESTKEY");
    assertThat(res).isEqualTo("TESTVAL");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "GET TESTKEY"),
                            equalTo(maybeStable(DB_OPERATION), "GET"))));
  }

  @Test
  void testGetNonExistentKeyCommand() {
    String res = syncCommands.get("NON_EXISTENT_KEY");
    assertThat(res).isNull();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(emitStableDatabaseSemconv() ? "GET " + host + ":" + port : "GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "GET NON_EXISTENT_KEY"),
                            equalTo(maybeStable(DB_OPERATION), "GET"))));
  }

  @Test
  void testCommandWithNoArguments() {
    String res = syncCommands.randomkey();
    assertThat(res).isNotNull();

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "RANDOMKEY " + host + ":" + port
                                : "RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "RANDOMKEY"),
                            equalTo(maybeStable(DB_OPERATION), "RANDOMKEY"))));
  }

  @Test
  void testListCommand() {
    long res = syncCommands.lpush("TESTLIST", "TESTLIST ELEMENT");
    assertThat(res).isEqualTo(1);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv() ? "LPUSH " + host + ":" + port : "LPUSH")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "LPUSH TESTLIST ?"),
                            equalTo(maybeStable(DB_OPERATION), "LPUSH"))));
  }

  @Test
  void testHashSetCommand() {
    String res = syncCommands.hmset("user", testHashMap);
    assertThat(res).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv() ? "HMSET " + host + ":" + port : "HMSET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "HMSET user firstname ? lastname ? age ?"),
                            equalTo(maybeStable(DB_OPERATION), "HMSET"))));
  }

  @Test
  void testHashGetallCommand() {
    Map<String, String> res = syncCommands.hgetall("TESTHM");
    assertThat(res).isEqualTo(testHashMap);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "HGETALL " + host + ":" + port
                                : "HGETALL")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), REDIS),
                            equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                            equalTo(maybeStable(DB_STATEMENT), "HGETALL TESTHM"),
                            equalTo(maybeStable(DB_OPERATION), "HGETALL"))));
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
                          span.hasName(
                                  emitStableDatabaseSemconv()
                                      ? "DEBUG " + host + ":" + port
                                      : "DEBUG")
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(SERVER_ADDRESS, host),
                                  equalTo(SERVER_PORT, port),
                                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                                  equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                                  equalTo(maybeStable(DB_STATEMENT), "DEBUG SEGFAULT"),
                                  equalTo(maybeStable(DB_OPERATION), "DEBUG"))));
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
                          span.hasName(
                                  emitStableDatabaseSemconv()
                                      ? "SHUTDOWN " + host + ":" + port
                                      : "SHUTDOWN")
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(SERVER_ADDRESS, host),
                                  equalTo(SERVER_PORT, port),
                                  equalTo(maybeStable(DB_SYSTEM), REDIS),
                                  equalTo(DB_NAMESPACE, emitStableDatabaseSemconv() ? "0" : null),
                                  equalTo(maybeStable(DB_STATEMENT), "SHUTDOWN NOSAVE"),
                                  equalTo(maybeStable(DB_OPERATION), "SHUTDOWN"))));
        });
  }
}
