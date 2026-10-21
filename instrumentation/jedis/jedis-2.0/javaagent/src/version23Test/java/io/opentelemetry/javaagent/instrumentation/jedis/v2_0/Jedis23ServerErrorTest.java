/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisDataException;

class Jedis23ServerErrorTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final GenericContainer<?> redisServer =
      new GenericContainer<>("redis:6.2.3-alpine").withExposedPorts(6379);

  private static Jedis jedis;

  @BeforeAll
  static void setup() {
    redisServer.start();
    cleanup.deferAfterAll(redisServer::stop);
    jedis = new Jedis(redisServer.getHost(), redisServer.getMappedPort(6379));
    cleanup.deferAfterAll(jedis::disconnect);
  }

  @BeforeEach
  void reset() {
    jedis.flushAll();
    testing.clearData();
  }

  @ParameterizedTest
  @MethodSource("serverErrorReplies")
  void serverErrorPrefix(String reply, String errorType) {
    Throwable error =
        catchThrowable(() -> jedis.eval("return redis.error_reply(ARGV[1])", 0, reply));

    assertThat(error).isInstanceOf(JedisDataException.class);
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span -> span.hasAttributesSatisfying(equalTo(ERROR_TYPE, errorType))));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-2.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .anySatisfy(
                            point ->
                                assertThat(point.getAttributes().get(ERROR_TYPE))
                                    .isEqualTo(errorType))));
  }

  private static Stream<Arguments> serverErrorReplies() {
    String longPrefix = "VeryLongPrefix_0123456789_0123456789_0123456789_0123456789_0123456789";
    return Stream.of(
        argumentSet("dedicated code", "MOVED 42 127.0.0.1:6379", "MOVED"),
        argumentSet("punctuation", "Custom.Code! details", "Custom.Code!"),
        argumentSet("lowercase", "lowercase details", "lowercase"),
        argumentSet("leading spaces", "  Custom.Code! details", "Custom.Code!"),
        argumentSet("non-ASCII prefix", "échec details", "échec"),
        argumentSet("long prefix", longPrefix + " details", longPrefix),
        argumentSet("prefix only", "PrefixOnly", "PrefixOnly"),
        argumentSet("tabs are not separators", "Custom.Code!\tmessage", "Custom.Code!\tmessage"),
        argumentSet("empty prefix", " ", JedisDataException.class.getName()));
  }
}
