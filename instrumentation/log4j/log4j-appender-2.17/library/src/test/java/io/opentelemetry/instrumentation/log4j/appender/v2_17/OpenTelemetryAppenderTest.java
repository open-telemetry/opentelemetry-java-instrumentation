/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenTelemetryAppenderTest extends AbstractOpenTelemetryAppenderTest {

  @BeforeEach
  void setup() {
    generalBeforeEachSetup();
    OpenTelemetryAppender.install(openTelemetry);
  }

  @Test
  void logWithSpan() { // Does not work for log replay but it is not likely to occur because
    // the log replay is related to the case where an OpenTelemetry object is not yet available
    // at the time the log is executed (and if no OpenTelemetry is available, the context
    // propagation can't happen)
    Span span1 = runWithSpan("span1", () -> logger.info("log message"));

    executeAfterLogsExecution();

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(1);
    assertThat(logDataList.get(0).getSpanContext()).isEqualTo(span1.getSpanContext());
  }

  private static Span runWithSpan(String spanName, Runnable runnable) {
    Span span = SdkTracerProvider.builder().build().get("tracer").spanBuilder(spanName).startSpan();
    try (Scope ignored = span.makeCurrent()) {
      runnable.run();
    } finally {
      span.end();
    }
    return span;
  }
}
