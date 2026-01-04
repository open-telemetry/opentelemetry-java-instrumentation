/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.lettuce.v5_1;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_ADDRESS;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_PEER_PORT;
import static io.opentelemetry.semconv.NetworkAttributes.NETWORK_TYPE;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;

import io.lettuce.core.api.sync.RedisCommands;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public abstract class AbstractLettuceQueryParameterTest extends AbstractLettuceClientTest {

  private static final AttributeKey<String> DB_QUERY_PARAMETER_0 =
      AttributeKey.stringKey("db.query.parameter.0");
  private static final AttributeKey<String> DB_QUERY_PARAMETER_1 =
      AttributeKey.stringKey("db.query.parameter.1");
  private static final AttributeKey<String> DB_QUERY_PARAMETER_2 =
      AttributeKey.stringKey("db.query.parameter.2");

  @BeforeAll
  void setUp() throws UnknownHostException {
    redisServer.start();

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    String embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    redisClient = createClient(embeddedDbUri);
    connection = redisClient.connect();

    testing().clearData();
  }

  @AfterAll
  static void cleanUp() {
    connection.close();
    redisClient.shutdown();
    redisServer.stop();
  }

  @Test
  void testSetCommandWithParameters() {
    String key = "test-key-1";
    String value = "test-value-1";

    RedisCommands<String, String> commands = connection.sync();
    testing()
        .runWithSpan(
            "parent",
            () -> {
              commands.set(key, value);
              return null;
            });

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, "ipv4"),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), "redis"),
                                    equalTo(maybeStable(DB_STATEMENT), "SET " + key + " " + value),
                                    equalTo(DB_QUERY_PARAMETER_0, key),
                                    equalTo(DB_QUERY_PARAMETER_1, value)))
                            .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));
  }

  @Test
  void testGetCommandWithSingleParameter() {
    String key = "test-key-2";

    RedisCommands<String, String> commands = connection.sync();
    commands.set(key, "test-value-2");
    testing().waitForTraces(1);
    testing().clearData();

    testing()
        .runWithSpan(
            "parent",
            () -> {
              commands.get(key);
              return null;
            });

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("GET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, "ipv4"),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), "redis"),
                                    equalTo(maybeStable(DB_STATEMENT), "GET " + key),
                                    equalTo(DB_QUERY_PARAMETER_0, key)))
                            .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));
  }

  @Test
  void testHsetCommandWithMultipleParameters() {
    String hash = "test-hash";
    String field = "test-field";
    String value = "test-value";

    RedisCommands<String, String> commands = connection.sync();
    testing()
        .runWithSpan(
            "parent",
            () -> {
              commands.hset(hash, field, value);
              return null;
            });

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("HSET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
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
                                        "HSET " + hash + " " + field + " " + value),
                                    equalTo(DB_QUERY_PARAMETER_0, hash),
                                    equalTo(DB_QUERY_PARAMETER_1, field),
                                    equalTo(DB_QUERY_PARAMETER_2, value)))
                            .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));
  }

  @Test
  void testParameterWithSpecialCharacters() {
    String key = "key:with:colons";
    String value = "value with spaces";

    RedisCommands<String, String> commands = connection.sync();
    testing()
        .runWithSpan(
            "parent",
            () -> {
              commands.set(key, value);
              return null;
            });

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span -> span.hasName("parent").hasKind(SpanKind.INTERNAL).hasNoParent(),
                    span ->
                        span.hasName("SET")
                            .hasKind(SpanKind.CLIENT)
                            .hasParent(trace.getSpan(0))
                            .hasAttributesSatisfyingExactly(
                                addExtraAttributes(
                                    equalTo(NETWORK_TYPE, "ipv4"),
                                    equalTo(NETWORK_PEER_ADDRESS, ip),
                                    equalTo(NETWORK_PEER_PORT, port),
                                    equalTo(SERVER_ADDRESS, host),
                                    equalTo(SERVER_PORT, port),
                                    equalTo(maybeStable(DB_SYSTEM), "redis"),
                                    equalTo(maybeStable(DB_STATEMENT), "SET " + key + " " + value),
                                    equalTo(DB_QUERY_PARAMETER_0, key),
                                    equalTo(DB_QUERY_PARAMETER_1, value)))
                            .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));
  }
}
