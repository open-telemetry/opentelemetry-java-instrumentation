/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Jedis;

class Jedis40ClientTest {
  @RegisterExtension
  static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  static GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  static int port;

  static Jedis jedis;

  @BeforeAll
  static void setupSpec() {
    redisServer.start();
    port = redisServer.getMappedPort(6379);
    jedis = new Jedis("localhost", port);
  }

  @AfterAll
  static void cleanupSpec() {
    redisServer.stop();
    jedis.close();
  }

  @BeforeEach
  void setup() {
    jedis.flushAll();
    testing.clearData();
  }

  @Test
  void setCommand() {
    jedis.set("foo", "bar");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "SET foo ?"),
                            equalTo(SemanticAttributes.DB_OPERATION, "SET"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, port),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            satisfies(
                                SemanticAttributes.NET_SOCK_PEER_NAME,
                                val -> val.isIn("localhost", "127.0.0.1")))));
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
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "SET foo ?"),
                            equalTo(SemanticAttributes.DB_OPERATION, "SET"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, port),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            satisfies(
                                SemanticAttributes.NET_SOCK_PEER_NAME,
                                val -> val.isIn("localhost", "127.0.0.1")))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "GET foo"),
                            equalTo(SemanticAttributes.DB_OPERATION, "GET"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, port),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            satisfies(
                                SemanticAttributes.NET_SOCK_PEER_NAME,
                                val -> val.isIn("localhost", "127.0.0.1")))));
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
                    span.hasName("SET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "SET foo ?"),
                            equalTo(SemanticAttributes.DB_OPERATION, "SET"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, port),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            satisfies(
                                SemanticAttributes.NET_SOCK_PEER_NAME,
                                val -> val.isIn("localhost", "127.0.0.1")))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(SemanticAttributes.DB_SYSTEM, "redis"),
                            equalTo(SemanticAttributes.DB_STATEMENT, "RANDOMKEY"),
                            equalTo(SemanticAttributes.DB_OPERATION, "RANDOMKEY"),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_PORT, port),
                            equalTo(SemanticAttributes.NET_SOCK_PEER_ADDR, "127.0.0.1"),
                            satisfies(
                                SemanticAttributes.NET_SOCK_PEER_NAME,
                                val -> val.isIn("localhost", "127.0.0.1")))));
  }
}
