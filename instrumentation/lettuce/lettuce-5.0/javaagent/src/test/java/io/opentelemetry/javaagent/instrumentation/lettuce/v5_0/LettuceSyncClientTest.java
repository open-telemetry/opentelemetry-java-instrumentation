/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;

import com.google.common.collect.ImmutableMap;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
class LettuceSyncClientTest extends AbstractLettuceClientTest {
  private static int incorrectPort;
  private static String dbUriNonExistent;

  private static final ImmutableMap<String, String> testHashMap =
      ImmutableMap.of(
          "firstname", "John",
          "lastname", "Doe",
          "age", "53");

  private static RedisCommands<String, String> syncCommands;

  @BeforeAll
  static void setUp() throws UnknownHostException {
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

    // 2 sets + 1 connect trace
    testing.waitForTraces(3);
    testing.clearData();
  }

  @AfterAll
  static void cleanUp() {
    connection.close();
    shutdown(redisClient);
    redisServer.stop();
  }

  @Test
  void testConnect() {
    RedisClient testConnectionClient = RedisClient.create(embeddedDbUri);
    testConnectionClient.setOptions(CLIENT_OPTIONS);

    StatefulRedisConnection<String, String> testConnection = testConnectionClient.connect();
    cleanup.deferCleanup(testConnection);
    cleanup.deferCleanup(testConnectionClient::shutdown);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("CONNECT")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SERVER_ADDRESS, host),
                            equalTo(SERVER_PORT, port),
                            equalTo(maybeStable(DB_SYSTEM), "redis"))));
  }

  @Test
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
                            equalTo(maybeStable(DB_SYSTEM), "redis"))
                        .hasEventsSatisfyingExactly(
                            event ->
                                event
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(
                                            AttributeKey.stringKey("exception.type"),
                                            "io.netty.channel.AbstractChannel.AnnotatedConnectException"),
                                        equalTo(
                                            AttributeKey.stringKey("exception.message"),
                                            "Connection refused: "
                                                + host
                                                + "/"
                                                + ip
                                                + ":"
                                                + incorrectPort),
                                        satisfies(
                                            AttributeKey.stringKey("exception.stacktrace"),
                                            AbstractAssert::isNotNull)))));
  }

  @Test
  void testSetCommand() {
    String res = syncCommands.set("TESTSETKEY", "TESTSETVAL");
    assertThat(res).isEqualTo("OK");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "SET TESTSETKEY ?"),
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
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
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
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
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
                    span.hasName("RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
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
                    span.hasName("LPUSH")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
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
                    span.hasName("HMSET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
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
                    span.hasName("HGETALL")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "HGETALL TESTHM"),
                            equalTo(maybeStable(DB_OPERATION), "HGETALL"))));
  }

  @Test
  void testDebugSegfaultCommandWithNoArgumentShouldProduceSpan() {
    // Test causes redis to crash therefore it needs its own container
    try (StatefulRedisConnection<String, String> statefulConnection = newContainerConnection()) {
      RedisCommands<String, String> commands = statefulConnection.sync();
      commands.debugSegfault();
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DEBUG")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "DEBUG SEGFAULT"),
                            equalTo(maybeStable(DB_OPERATION), "DEBUG"))));
  }

  @Test
  void testShutdownCommandShouldProduceSpan() {
    // Test causes redis to crash therefore it needs its own container
    try (StatefulRedisConnection<String, String> statefulConnection = newContainerConnection()) {
      RedisCommands<String, String> commands = statefulConnection.sync();
      commands.shutdown(false);
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SHUTDOWN")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), "redis"),
                            equalTo(maybeStable(DB_STATEMENT), "SHUTDOWN NOSAVE"),
                            equalTo(maybeStable(DB_OPERATION), "SHUTDOWN"))));
  }
}
