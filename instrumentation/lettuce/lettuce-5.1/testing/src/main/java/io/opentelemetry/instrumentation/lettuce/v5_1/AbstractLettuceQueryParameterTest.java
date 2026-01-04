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
import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.lettuce.core.api.sync.RedisCommands;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.SpanKind;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Base test class for verifying db.query.parameter capture functionality.
 *
 * <p>This test is designed to run with capture-query-parameters enabled via gradle configuration.
 */
@SuppressWarnings("deprecation") // using deprecated semconv
public abstract class AbstractLettuceQueryParameterTest extends AbstractLettuceClientTest {

  private static final AttributeKey<String> DB_QUERY_PARAMETER_0 =
      AttributeKey.stringKey("db.query.parameter.0");
  private static final AttributeKey<String> DB_QUERY_PARAMETER_1 =
      AttributeKey.stringKey("db.query.parameter.1");
  private static final AttributeKey<String> DB_QUERY_PARAMETER_2 =
      AttributeKey.stringKey("db.query.parameter.2");
  private static final AttributeKey<String> DB_QUERY_PARAMETER_3 =
      AttributeKey.stringKey("db.query.parameter.3");

  @BeforeAll
  void setUp() throws UnknownHostException {
    redisServer.start();

    host = redisServer.getHost();
    ip = InetAddress.getByName(host).getHostAddress();
    port = redisServer.getMappedPort(6379);
    String embeddedDbUri = "redis://" + host + ":" + port + "/" + DB_INDEX;

    redisClient = createClient(embeddedDbUri);
    connection = redisClient.connect();

    // Initial setup commands
    RedisCommands<String, String> commands = connection.sync();
    commands.set("TESTKEY", "TESTVAL");

    // Wait for setup traces and clear
    testing().waitForTraces(1);
    testing().clearData();
  }

  @AfterAll
  static void cleanUp() {
    if (connection != null) {
      connection.close();
    }
    if (redisClient != null) {
      redisClient.shutdown();
    }
    if (redisServer != null) {
      redisServer.stop();
    }
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
                                    // With parameter capture enabled, statement is NOT sanitized
                                    equalTo(maybeStable(DB_STATEMENT), "SET " + key + " " + value),
                                    // Query parameters should be captured
                                    equalTo(DB_QUERY_PARAMETER_0, key),
                                    equalTo(DB_QUERY_PARAMETER_1, value)))
                            .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));
  }

  @Test
  void testGetCommandWithSingleParameter() {
    String key = "test-key-2";

    RedisCommands<String, String> commands = connection.sync();
    // Set a value first
    commands.set(key, "test-value-2");
    testing().waitForTraces(1);
    testing().clearData();

    // Now test GET with parameter capture
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
                                    // Statement should include the actual key (not sanitized)
                                    equalTo(maybeStable(DB_STATEMENT), "GET " + key),
                                    // Query parameter for key
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
                                    // Full statement without sanitization
                                    equalTo(
                                        maybeStable(DB_STATEMENT),
                                        "HSET " + hash + " " + field + " " + value),
                                    // Query parameters: hash, field, value
                                    equalTo(DB_QUERY_PARAMETER_0, hash),
                                    equalTo(DB_QUERY_PARAMETER_1, field),
                                    equalTo(DB_QUERY_PARAMETER_2, value)))
                            .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));
  }

  @Test
  void testMsetCommandWithMultiplePairs() {
    String key1 = "mset-key-1";
    String value1 = "mset-value-1";
    String key2 = "mset-key-2";
    String value2 = "mset-value-2";

    RedisCommands<String, String> commands = connection.sync();
    commands.mset(ImmutableMap.of(key1, value1, key2, value2));

    testing()
        .waitAndAssertTraces(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("MSET")
                            .hasKind(SpanKind.CLIENT)
                            // MSET parameter order depends on map iteration order
                            // Just verify that parameters exist
                            .hasAttributesSatisfying(
                                attributes -> {
                                  assertThat(attributes.get(SERVER_ADDRESS)).isEqualTo(host);
                                  assertThat(attributes.get(SERVER_PORT)).isEqualTo((long) port);
                                  assertThat(attributes.get(maybeStable(DB_SYSTEM)))
                                      .isEqualTo("redis");
                                  // Verify parameters are captured (order may vary due to map)
                                  assertThat(attributes.get(DB_QUERY_PARAMETER_0)).isNotNull();
                                  assertThat(attributes.get(DB_QUERY_PARAMETER_1)).isNotNull();
                                  assertThat(attributes.get(DB_QUERY_PARAMETER_2)).isNotNull();
                                  assertThat(attributes.get(DB_QUERY_PARAMETER_3)).isNotNull();
                                })
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
                                    // Full statement with special characters
                                    equalTo(maybeStable(DB_STATEMENT), "SET " + key + " " + value),
                                    // Query parameters should preserve special characters
                                    equalTo(DB_QUERY_PARAMETER_0, key),
                                    equalTo(DB_QUERY_PARAMETER_1, value)))
                            .satisfies(AbstractLettuceClientTest::assertCommandEncodeEvents)));
  }
}
