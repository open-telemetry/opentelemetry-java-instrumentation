/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.javaagent.testing.common.AgentTestingExporterAccess;
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
  void exportAndRetrieve() {
    GlobalOpenTelemetry.getTracer("test").spanBuilder("test").startSpan().end();

    List<SpanData> spans = AgentTestingExporterAccess.getExportedSpans();
    assertEquals(1, spans.size());
    assertEquals("test", spans.get(0).getName());
  }
}
