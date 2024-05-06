/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.semconv.ServerAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Jedis;

public abstract class AbstractJedisTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final GenericContainer<?> REDIS_SERVER =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private static String host;

  private static int port;

  private static Jedis jedis;

  @BeforeAll
  static void setup() {
    REDIS_SERVER.start();
    host = REDIS_SERVER.getHost();
    port = REDIS_SERVER.getMappedPort(6379);
    jedis = new Jedis(host, port);
  }

  @AfterAll
  static void cleanup() {
    REDIS_SERVER.stop();
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
                            equalTo(ServerAttributes.SERVER_ADDRESS, host),
                            equalTo(ServerAttributes.SERVER_PORT, port))));
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
                            equalTo(ServerAttributes.SERVER_ADDRESS, host),
                            equalTo(ServerAttributes.SERVER_PORT, port))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                            equalTo(DbIncubatingAttributes.DB_STATEMENT, "GET foo"),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "GET"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, host),
                            equalTo(ServerAttributes.SERVER_PORT, port))));
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
                            equalTo(ServerAttributes.SERVER_ADDRESS, host),
                            equalTo(ServerAttributes.SERVER_PORT, port))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("RANDOMKEY")
                        .hasKind(SpanKind.CLIENT)
                        .hasAttributesSatisfyingExactly(
                            equalTo(DbIncubatingAttributes.DB_SYSTEM, "redis"),
                            equalTo(DbIncubatingAttributes.DB_STATEMENT, "RANDOMKEY"),
                            equalTo(DbIncubatingAttributes.DB_OPERATION, "RANDOMKEY"),
                            equalTo(ServerAttributes.SERVER_ADDRESS, host),
                            equalTo(ServerAttributes.SERVER_PORT, port))));
  }
}
