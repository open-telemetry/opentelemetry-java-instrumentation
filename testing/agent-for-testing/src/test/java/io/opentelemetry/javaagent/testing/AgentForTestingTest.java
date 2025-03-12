/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    assertEquals(0, AgentTestingExporterAccess.getExportedSpans().size());
  }

  @Test
  void exportAndRetrieveSpans() {
    GlobalOpenTelemetry.getTracer("test").spanBuilder("test").startSpan().end();

    List<SpanData> spans = AgentTestingExporterAccess.getExportedSpans();
    assertEquals(1, spans.size());
    assertEquals("test", spans.get(0).getName());
  }

  @Test
  void exportAndRetrieveMetrics() {
    GlobalOpenTelemetry.getMeter("test").upDownCounterBuilder("test").build().add(1);

    List<MetricData> metrics = AgentTestingExporterAccess.getExportedMetrics();
    assertEquals(1, metrics.size());
    assertEquals("test", metrics.get(0).getName());
  }

  @Test
  void exportAndRetrieveLogRecords() {
    Logger logger = GlobalOpenTelemetry.get().getLogsBridge().loggerBuilder("test").build();
    logger.logRecordBuilder().setBody("testBody").emit();

    List<LogRecordData> logRecords = AgentTestingExporterAccess.getExportedLogRecords();
    assertEquals(1, logRecords.size());
    assertEquals("testBody", logRecords.get(0).getBodyValue().getValue());
  }
}
