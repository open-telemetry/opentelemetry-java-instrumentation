/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.NetworkAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import java.net.InetAddress;
import java.net.UnknownHostException;
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

  static String ip;

  static int port;

  static Jedis jedis;

  @BeforeAll
  static void setup() throws UnknownHostException {
    redisServer.start();
    port = redisServer.getMappedPort(6379);
    ip = InetAddress.getByName(redisServer.getHost()).getHostAddress();
    jedis = new Jedis(redisServer.getHost(), port);
  }

  @AfterAll
  static void cleanup() {
    redisServer.stop();
    jedis.close();
  }

  @BeforeEach
  void reset() {
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
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                            equalTo(DbIncubatingAttributes.DB_STATEMENT, "SET foo ?"),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "SET"),
                            equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                            equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip))));
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
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                            equalTo(DbIncubatingAttributes.DB_STATEMENT, "SET foo ?"),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "SET"),
                            equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                            equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                            equalTo(DbIncubatingAttributes.DB_STATEMENT, "GET foo"),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "GET"),
                            equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                            equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip))));
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
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                            equalTo(DbIncubatingAttributes.DB_STATEMENT, "SET foo ?"),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "SET"),
                            equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                            equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                            equalTo(DbIncubatingAttributes.DB_STATEMENT, "RANDOMKEY"),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "RANDOMKEY"),
                            equalTo(NetworkAttributes.NETWORK_TYPE, "ipv4"),
                            equalTo(NetworkAttributes.NETWORK_PEER_PORT, port),
                            equalTo(NetworkAttributes.NETWORK_PEER_ADDRESS, ip))));
  }
}
