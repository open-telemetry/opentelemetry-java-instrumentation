/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.instrumentation.testing.util.TestLatestDeps.testLatestDeps;
import static io.opentelemetry.semconv.DbAttributes.DB_OPERATION_NAME;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_TEXT;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import redis.clients.jedis.BuilderFactory;
import redis.clients.jedis.CommandArguments;
import redis.clients.jedis.CommandObject;
import redis.clients.jedis.Connection;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Protocol;

class JedisConnectionInitializationTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @Test
  void standaloneInitializationCompletesCommandsAtTheirReplies() throws IOException {
    boolean jedis51OrLater = Boolean.getBoolean("testJedis51OrLater");
    boolean latestJedis = testLatestDeps();
    String replies =
        (latestJedis ? "%1\r\n+proto\r\n:3\r\n" : "")
            + "+OK\r\n"
            + (jedis51OrLater ? "-ERR unsupported SETINFO\r\n-ERR unsupported SETINFO\r\n" : "")
            + "+PONG\r\n";
    try (ServerSocket server = new ServerSocket(PortUtils.findOpenPort());
        Socket socket = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
        Socket peer = server.accept()) {
      peer.getOutputStream().write(replies.getBytes(UTF_8));
      try (Connection connection =
          new Connection(
              () -> socket,
              DefaultJedisClientConfig.builder().clientName("otel-initialization-test").build())) {
        assertThat(
                connection.executeCommand(
                    new CommandObject<>(
                        new CommandArguments(Protocol.Command.PING), BuilderFactory.STRING)))
            .isEqualTo("PONG");
      }
    }

    int initializationCommands = jedis51OrLater ? 3 : 1;
    int expectedSpans = initializationCommands + 1 + (latestJedis ? 1 : 0);
    testing.waitForTraces(expectedSpans);
    assertThat(testing.spans()).hasSize(expectedSpans);
    assertThat(testing.spans())
        .filteredOn(
            span -> {
              String query = span.getAttributes().get(DB_QUERY_TEXT);
              return query != null && query.contains("SETINFO");
            })
        .hasSize(jedis51OrLater ? 2 : 0)
        .allSatisfy(span -> assertThat(span.getAttributes().get(ERROR_TYPE)).isEqualTo("ERR"));
    assertThat(testing.spans())
        .filteredOn(span -> "PING".equals(span.getAttributes().get(DB_OPERATION_NAME)))
        .singleElement()
        .satisfies(span -> assertThat(span.getAttributes().get(ERROR_TYPE)).isNull());
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-4.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric -> {
                  assertThat(metric.getHistogramData().getPoints())
                      .filteredOn(
                          point -> "CLIENT".equals(point.getAttributes().get(DB_OPERATION_NAME)))
                      .filteredOn(point -> "ERR".equals(point.getAttributes().get(ERROR_TYPE)))
                      .hasSize(jedis51OrLater ? 1 : 0)
                      .allSatisfy(point -> assertThat(point.getCount()).isEqualTo(2));
                  assertThat(metric.getHistogramData().getPoints())
                      .filteredOn(
                          point -> "CLIENT".equals(point.getAttributes().get(DB_OPERATION_NAME)))
                      .filteredOn(point -> point.getAttributes().get(ERROR_TYPE) == null)
                      .singleElement()
                      .satisfies(point -> assertThat(point.getCount()).isEqualTo(1));
                }));
  }
}
