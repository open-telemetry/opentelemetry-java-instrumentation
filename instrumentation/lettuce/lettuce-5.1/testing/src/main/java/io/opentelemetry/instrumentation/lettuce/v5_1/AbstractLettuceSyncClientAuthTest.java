/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.api.sync.RedisCommands;
import io.opentelemetry.api.trace.SpanKind;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractLettuceSyncClientAuthTest extends AbstractLettuceClientTest {

  @BeforeAll
  void setUp() throws UnknownHostException {
    redisServer = redisServer.withCommand("redis-server", "--requirepass password");
    redisServer.start();

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    redisClient = createClient(embeddedDbUri);
    redisClient.setOptions(LettuceTestUtil.CLIENT_OPTIONS);
  }

  @AfterAll
  static void cleanUp() {
    redisClient.shutdown();
    redisServer.stop();

    // Set back so other tests don't fail due to NOAUTH error
    redisServer = redisServer.withCommand("redis-server", "--requirepass \"\"");
  }

  @Test
  void testAuthCommand() throws Exception {
    Class<?> commandsClass = RedisCommands.class;
    java.lang.reflect.Method authMethod;
    // the auth() argument type changed between 5.x -> 6.x
    try {
      authMethod = commandsClass.getMethod("auth", String.class);
    } catch (NoSuchMethodException unused) {
      authMethod = commandsClass.getMethod("auth", CharSequence.class);
    }

    String result = (String) authMethod.invoke(redisClient.connect().sync(), "password");

    assertThat(result).isEqualTo("OK");

    if (Boolean.getBoolean("testLatestDeps")) {
      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("CLIENT")
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_TYPE, "ipv4"),
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(maybeStable(DB_SYSTEM), "redis"),
                                      equalTo(
                                          maybeStable(DB_STATEMENT),
                                          "CLIENT SETINFO lib-name Lettuce")))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("CLIENT")
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_TYPE, "ipv4"),
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(maybeStable(DB_SYSTEM), "redis"),
                                      satisfies(
                                          maybeStable(DB_STATEMENT),
                                          stringAssert ->
                                              stringAssert.startsWith("CLIENT SETINFO lib-ver"))))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("AUTH")
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_TYPE, "ipv4"),
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(maybeStable(DB_SYSTEM), "redis"),
                                      equalTo(maybeStable(DB_STATEMENT), "AUTH ?")))
                              .hasEventsSatisfyingExactly(
                                  event -> event.hasName("redis.encode.start"),
                                  event -> event.hasName("redis.encode.end"))));

    } else {
      testing()
          .waitAndAssertTraces(
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName("AUTH")
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_TYPE, "ipv4"),
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(maybeStable(DB_SYSTEM), "redis"),
                                      equalTo(maybeStable(DB_STATEMENT), "AUTH ?")))
                              .hasEventsSatisfyingExactly(
                                  event -> event.hasName("redis.encode.start"),
                                  event -> event.hasName("redis.encode.end"))));
    }
  }
}
