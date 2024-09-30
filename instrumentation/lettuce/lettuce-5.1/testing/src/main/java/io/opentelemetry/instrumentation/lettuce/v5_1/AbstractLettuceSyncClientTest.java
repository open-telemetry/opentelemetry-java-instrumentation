/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.google.common.collect.ImmutableMap;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.RedisException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractLettuceSyncClientTest extends AbstractLettuceClientTest {

  private static String dbUriNonExistent;

  private static final ImmutableMap<String, String> testHashMap =
      ImmutableMap.of(
          "firstname", "John",
          "lastname", "Doe",
          "age", "53");

  private static RedisCommands<String, String> syncCommands;

  @BeforeAll
  void setUp() throws UnknownHostException {
    redisServer.start();

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    int incorrectPort = PortUtils.findOpenPort();
    dbUriNonExistent = "redis://" + host + ":" + incorrectPort + "/" + DB_INDEX;

    redisClient = createClient(embeddedDbUri);
    redisClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS);

    connection = redisClient.connect();
    syncCommands = connection.sync();

    syncCommands.set("TESTKEY", "TESTVAL");
    syncCommands.hmset("TESTHM", testHashMap);

    // 2 sets
    getInstrumentationExtension().waitForTraces(2);
    getInstrumentationExtension().clearData();
  }

  @AfterAll
  static void cleanUp() {
    connection.close();
    redisClient.shutdown();
    redisServer.stop();
  }

  @Test
  void testConnect() {
    StatefulRedisConnection<String, String> testConnection = redisClient.connect();
    cleanup.deferCleanup(testConnection);

    if (Boolean.getBoolean("testLatestDeps")) {
      // ignore CLIENT SETINFO traces
      getInstrumentationExtension().waitForTraces(2);
    } else {
      // Lettuce tracing does not trace connect
      assertThat(getInstrumentationExtension().spans()).isEmpty();
    }
  }

  @Test
  void testConnectException() {
    RedisClient testConnectionClient = RedisClient.create(dbUriNonExistent);
    testConnectionClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS);
    cleanup.deferCleanup(testConnectionClient::shutdown);

    Throwable thrown = catchThrowable(testConnectionClient::connect);

    assertThat(thrown).isInstanceOf(RedisConnectionException.class);

    // Lettuce tracing does not trace connect
    assertThat(getInstrumentationExtension().spans()).isEmpty();
  }

  @Test
  void testSetCommand() {
    String res = syncCommands.set("TESTSETKEY", "TESTSETVAL");
    assertThat(res).isEqualTo("OK");

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "SET TESTSETKEY ?"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  @Test
  void testGetCommand() {
    String res = syncCommands.get("TESTKEY");
    assertThat(res).isEqualTo("TESTVAL");

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "GET TESTKEY"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  @Test
  void testGetNonExistentKeyCommand() {
    String res = syncCommands.get("NON_EXISTENT_KEY");
    assertThat(res).isNull();

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(
                                    DbIncubatingAttributes.DB_STATEMENT, "GET NON_EXISTENT_KEY"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  @Test
  void testCommandWithNoArguments() {
    String res = syncCommands.randomkey();
    assertThat(res).isNotNull();

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("RANDOMKEY")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "RANDOMKEY"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  @Test
  void testListCommand() {
    // Needs its own container or flaky from inconsistent command count
    ContainerConnection containerConnection = newContainerConnection();
    RedisCommands<String, String> commands = containerConnection.connection.sync();

    if (Boolean.getBoolean("testLatestDeps")) {
      // ignore CLIENT SETINFO traces
      getInstrumentationExtension().waitForTraces(2);
      getInstrumentationExtension().clearData();
    }

    long res = commands.lpush("TESTLIST", "TESTLIST ELEMENT");
    assertThat(res).isEqualTo(1);

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("LPUSH")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(
                                    NetworkAttributes.NETWORK_PEER_PORT, containerConnection.port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, containerConnection.port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "LPUSH TESTLIST ?"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  @Test
  void testHashSetCommand() {
    String res = syncCommands.hmset("user", testHashMap);
    assertThat(res).isEqualTo("OK");

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("HMSET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(
                                    DbIncubatingAttributes.DB_STATEMENT,
                                    "HMSET user firstname ? lastname ? age ?"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  @Test
  void testHashGetallCommand() {
    Map<String, String> res = syncCommands.hgetall("TESTHM");
    assertThat(res).isEqualTo(testHashMap);

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("HGETALL")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "HGETALL TESTHM"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  @Test
  void testEvalCommand() {
    String script =
        "redis.call('lpush', KEYS[1], ARGV[1], ARGV[2]); return redis.call('llen', KEYS[1])";

    Long result =
        syncCommands.eval(
            script, ScriptOutputType.INTEGER, new String[] {"TESTLIST"}, "abc", "def");
    assertThat(result).isEqualTo(2);

    String b64Script = Base64.getEncoder().encodeToString(script.getBytes(UTF_8));

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("EVAL")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(
                                    DbIncubatingAttributes.DB_STATEMENT,
                                    "EVAL " + b64Script + " 1 TESTLIST ? ?"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  @Test
  void testMsetCommand() {
    String result = syncCommands.mset(ImmutableMap.of("key1", "value1", "key2", "value2"));

    assertThat(result).isEqualTo("OK");

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("MSET")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "MSET key1 ? key2 ?"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end"))));
  }

  @Test
  void testDebugSegfaultCommandWithNoArgumentProducesNoSpan() {
    // Test causes redis to crash therefore it needs its own container
    ContainerConnection containerConnection = newContainerConnection();
    RedisCommands<String, String> commands = containerConnection.connection.sync();

    commands.debugSegfault();

    if (Boolean.getBoolean("testLatestDeps")) {
      getInstrumentationExtension()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("CLIENT")
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                  equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                  equalTo(
                                      NetworkAttributes.NETWORK_PEER_PORT,
                                      containerConnection.port),
                                  equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                  equalTo(ServerAttributes.SERVER_PORT, containerConnection.port),
                                  equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                  equalTo(
                                      DbIncubatingAttributes.DB_STATEMENT,
                                      "CLIENT SETINFO lib-name Lettuce"))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("CLIENT")
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                  equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                  equalTo(
                                      NetworkAttributes.NETWORK_PEER_PORT,
                                      containerConnection.port),
                                  equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                  equalTo(ServerAttributes.SERVER_PORT, containerConnection.port),
                                  equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                  satisfies(
                                      DbIncubatingAttributes.DB_STATEMENT,
                                      stringAssert ->
                                          stringAssert.startsWith("CLIENT SETINFO lib-ver")))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("DEBUG")
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                  equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                  equalTo(
                                      NetworkAttributes.NETWORK_PEER_PORT,
                                      containerConnection.port),
                                  equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                  equalTo(ServerAttributes.SERVER_PORT, containerConnection.port),
                                  equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                  equalTo(DbIncubatingAttributes.DB_STATEMENT, "DEBUG SEGFAULT"))));
    } else {
      getInstrumentationExtension()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("DEBUG")
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                  equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                  equalTo(
                                      NetworkAttributes.NETWORK_PEER_PORT,
                                      containerConnection.port),
                                  equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                  equalTo(ServerAttributes.SERVER_PORT, containerConnection.port),
                                  equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                  equalTo(DbIncubatingAttributes.DB_STATEMENT, "DEBUG SEGFAULT"))
                              // these are no longer recorded since Lettuce 6.1.6
                              .hasEventsSatisfyingExactly(
                                  event -> event.hasName("redis.encode.start"),
                                  event -> event.hasName("redis.encode.end"))));
    }
  }

  @Test
  void testShutdownCommandProducesNoSpan() {
    // Test causes redis to crash therefore it needs its own container
    ContainerConnection containerConnection = newContainerConnection();
    RedisCommands<String, String> commands = containerConnection.connection.sync();

    if (Boolean.getBoolean("testLatestDeps")) {
      // ignore CLIENT SETINFO traces
      getInstrumentationExtension().waitForTraces(2);
      getInstrumentationExtension().clearData();
    }

    commands.shutdown(false);

    getInstrumentationExtension()
        .waitAndAssertTraces(
            trace -> {
              if (Boolean.getBoolean("testLatestDeps")) {
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SHUTDOWN")
                            .hasKind(SpanKind.CLIENT)
                            // Seems to only be treated as an error with Lettuce 6+
                            .hasException(new RedisException("Connection disconnected"))
                            .hasAttributesSatisfyingExactly(
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(
                                    NetworkAttributes.NETWORK_PEER_PORT, containerConnection.port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, containerConnection.port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "SHUTDOWN NOSAVE")));
              } else {
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("SHUTDOWN")
                            .hasKind(SpanKind.CLIENT)
                            .hasAttributesSatisfyingExactly(
                                equalTo(AttributeKey.stringKey("error"), "Connection disconnected"),
                                equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                                equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip),
                                equalTo(
                                    NetworkAttributes.NETWORK_PEER_PORT, containerConnection.port),
                                equalTo(ServerAttributes.SERVER_ADDRESS, host),
                                equalTo(ServerAttributes.SERVER_PORT, containerConnection.port),
                                equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                                equalTo(DbIncubatingAttributes.DB_STATEMENT, "SHUTDOWN NOSAVE"))
                            .hasEventsSatisfyingExactly(
                                event -> event.hasName("redis.encode.start"),
                                event -> event.hasName("redis.encode.end")));
              }
            });
  }
}
