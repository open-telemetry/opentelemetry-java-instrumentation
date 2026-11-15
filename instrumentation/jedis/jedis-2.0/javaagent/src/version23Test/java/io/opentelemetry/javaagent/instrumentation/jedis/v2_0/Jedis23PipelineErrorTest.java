/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.PipelineBlock;
import redis.clients.jedis.exceptions.JedisDataException;

class Jedis23PipelineErrorTest {
  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Test
  void wrappedPipelineServerErrorUsesPrefixAndDoesNotLeak() throws IOException {
    try (ServerSocket server = new ServerSocket(0)) {
      server.setSoTimeout(10_000);
      Jedis jedis = new Jedis("127.0.0.1", server.getLocalPort());
      cleanup.deferCleanup(jedis::disconnect);
      jedis.connect();

      try (Socket connection = server.accept()) {
        connection.getOutputStream().write("+OK\r\n-WRONGTYPE details\r\n".getBytes(US_ASCII));

        List<Object> results =
            jedis.pipelined(
                new PipelineBlock() {
                  @Override
                  public void execute() {
                    set("success", "value");
                    get("wrong-type");
                  }
                });

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isEqualTo("OK");
        assertThat(results.get(1)).isInstanceOf(JedisDataException.class);
        assertThat((Throwable) results.get(1))
            .hasCauseInstanceOf(JedisDataException.class)
            .hasRootCauseMessage("WRONGTYPE details");
        assertErrorType("WRONGTYPE");

        testing.clearData();
        connection.getOutputStream().write("+OK\r\n".getBytes(US_ASCII));
        assertThat(jedis.set("after-error", "value")).isEqualTo("OK");
        assertErrorType(null);
      }
    }
  }

  private static void assertErrorType(String errorType) {
    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.satisfies(
                        spanData ->
                            assertThat(spanData.getAttributes().get(ERROR_TYPE))
                                .isEqualTo(errorType))));
    testing.waitAndAssertMetrics(
        "io.opentelemetry.jedis-2.0",
        "db.client.operation.duration",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric.getHistogramData().getPoints())
                        .allSatisfy(
                            point ->
                                assertThat(point.getAttributes().get(ERROR_TYPE))
                                    .isEqualTo(errorType))));
  }
}
