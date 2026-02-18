/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.javaagent.testing.common.AgentTestingExporterAccess;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentForTestingTest {

  @BeforeEach
  void reset() {
    AgentTestingExporterAccess.reset();
  }

  @Test
  void empty() {
    assertThat(AgentTestingExporterAccess.getExportedSpans().size()).isEqualTo(0);
  }

  @Test
  void exportAndRetrieveSpans() {
    GlobalOpenTelemetry.getTracer("test").spanBuilder("test").startSpan().end();

    List<SpanData> spans = AgentTestingExporterAccess.getExportedSpans();
    assertThat(spans.size()).isEqualTo(1);
    assertThat(spans.get(0).getName()).isEqualTo("test");
  }

  @Test
  void exportAndRetrieveMetrics() {
    GlobalOpenTelemetry.getMeter("test").upDownCounterBuilder("test").build().add(1);

    List<MetricData> metrics = AgentTestingExporterAccess.getExportedMetrics();
    assertThat(metrics.size()).isEqualTo(1);
    assertThat(metrics.get(0).getName()).isEqualTo("test");
  }

  @Test
  void exportAndRetrieveLogRecords() {
    Logger logger = GlobalOpenTelemetry.get().getLogsBridge().loggerBuilder("test").build();
    logger.logRecordBuilder().setBody("testBody").emit();

    List<LogRecordData> logRecords = AgentTestingExporterAccess.getExportedLogRecords();
    assertThat(logRecords.size()).isEqualTo(1);
    assertThat(logRecords.get(0).getBodyValue().getValue()).isEqualTo("testBody");
  }
}
