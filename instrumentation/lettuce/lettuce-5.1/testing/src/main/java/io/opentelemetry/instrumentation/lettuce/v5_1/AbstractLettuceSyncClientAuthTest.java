/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import io.opentelemetry.api.trace.SpanKind;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractLettuceSyncClientAuthTest extends AbstractLettuceClientTest {

  @BeforeAll
  void setUp() throws UnknownHostException {
    redisServer = redisServer.withCommand("redis-server", "--requirepass password");
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    redisClient = createClient(embeddedDbUri);
    cleanup.deferAfterAll(redisClient::shutdown);
    redisClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS);
  }

  @Test
  @SuppressWarnings("deprecation") // using deprecated semconv
  void testAuthCommand() throws ReflectiveOperationException {
    Class<?> commandsClass = RedisCommands.class;
    Method authMethod;
    // the auth() argument type changed between 5.x -> 6.x
    try {
      authMethod = commandsClass.getMethod("auth", String.class);
    } catch (NoSuchMethodException ignored) {
      authMethod = commandsClass.getMethod("auth", CharSequence.class);
    }

    StatefulRedisConnection<String, String> testConnection = redisClient.connect();
    cleanup.deferCleanup(testConnection);
    String result = (String) authMethod.invoke(testConnection.sync(), "password");

    assertThat(result).isEqualTo("OK");

    if (testLatestDeps()) {
      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName("CLIENT"))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(DB_SYSTEM_NAME, REDIS),
                                      equalTo(DB_QUERY_TEXT, "CLIENT SETINFO lib-name Lettuce"),
                                      equalTo(DB_OPERATION_NAME, "CLIENT"),
                                      equalTo(ERROR_TYPE, "NOAUTH")))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName("CLIENT"))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(DB_SYSTEM_NAME, REDIS),
                                      satisfies(
                                          DB_QUERY_TEXT,
                                          val -> val.startsWith("CLIENT SETINFO lib-ver")),
                                      equalTo(DB_OPERATION_NAME, "CLIENT"),
                                      equalTo(ERROR_TYPE, "NOAUTH")))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName("CLIENT"))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(DB_SYSTEM_NAME, REDIS),
                                      satisfies(
                                          DB_QUERY_TEXT,
                                          val -> val.startsWith("CLIENT MAINT_NOTIFICATIONS")),
                                      equalTo(DB_OPERATION_NAME, "CLIENT"),
                                      equalTo(ERROR_TYPE, "NOAUTH")))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName("AUTH"))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(DB_SYSTEM_NAME, REDIS),
                                      equalTo(DB_QUERY_TEXT, "AUTH ?"),
                                      equalTo(DB_OPERATION_NAME, "AUTH")))
                              .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));

    } else {
      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName("AUTH"))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(DB_SYSTEM_NAME, REDIS),
                                      equalTo(DB_QUERY_TEXT, "AUTH ?"),
                                      equalTo(DB_OPERATION_NAME, "AUTH")))
                              .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));
    }
  }
}
