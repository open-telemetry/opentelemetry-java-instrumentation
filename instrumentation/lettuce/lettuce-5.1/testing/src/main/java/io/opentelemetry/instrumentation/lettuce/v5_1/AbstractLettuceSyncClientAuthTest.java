/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitOldDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
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
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.REDIS;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.api.sync.RedisCommands;
import io.opentelemetry.api.trace.SpanKind;
import java.lang.reflect.Method;
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
    Method authMethod;
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
                          span.hasName(spanName("CLIENT"))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(maybeStable(DB_SYSTEM), REDIS),
                                      equalTo(
                                          maybeStable(DB_STATEMENT),
                                          "CLIENT SETINFO lib-name Lettuce"),
                                      equalTo(maybeStable(DB_OPERATION), "CLIENT"),
                                      equalTo(
                                          ERROR_TYPE,
                                          emitStableDatabaseSemconv()
                                              ? "io.lettuce.core.RedisCommandExecutionException"
                                              : null)))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName("CLIENT"))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(maybeStable(DB_SYSTEM), REDIS),
                                      satisfies(
                                          maybeStable(DB_STATEMENT),
                                          stringAssert ->
                                              stringAssert.startsWith("CLIENT SETINFO lib-ver")),
                                      equalTo(maybeStable(DB_OPERATION), "CLIENT"),
                                      equalTo(
                                          ERROR_TYPE,
                                          emitStableDatabaseSemconv()
                                              ? "io.lettuce.core.RedisCommandExecutionException"
                                              : null)))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName("CLIENT"))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(maybeStable(DB_SYSTEM), REDIS),
                                      satisfies(
                                          maybeStable(DB_STATEMENT),
                                          stringAssert ->
                                              stringAssert.startsWith(
                                                  "CLIENT MAINT_NOTIFICATIONS")),
                                      equalTo(maybeStable(DB_OPERATION), "CLIENT"),
                                      equalTo(
                                          ERROR_TYPE,
                                          emitStableDatabaseSemconv()
                                              ? "io.lettuce.core.RedisCommandExecutionException"
                                              : null)))),
              trace ->
                  trace.hasSpansSatisfyingExactly(
                      span ->
                          span.hasName(spanName("AUTH"))
                              .hasKind(SpanKind.CLIENT)
                              .hasAttributesSatisfyingExactly(
                                  addExtraAttributes(
                                      equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(maybeStable(DB_SYSTEM), REDIS),
                                      equalTo(maybeStable(DB_STATEMENT), "AUTH ?"),
                                      equalTo(maybeStable(DB_OPERATION), "AUTH")))
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
                                      equalTo(NETWORK_TYPE, emitOldDatabaseSemconv() ? IPV4 : null),
                                      equalTo(NETWORK_PEER_ADDRESS, ip),
                                      equalTo(NETWORK_PEER_PORT, port),
                                      equalTo(SERVER_ADDRESS, host),
                                      equalTo(SERVER_PORT, port),
                                      equalTo(maybeStable(DB_SYSTEM), REDIS),
                                      equalTo(maybeStable(DB_STATEMENT), "AUTH ?"),
                                      equalTo(maybeStable(DB_OPERATION), "AUTH")))
                              .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));
    }
  }
}
