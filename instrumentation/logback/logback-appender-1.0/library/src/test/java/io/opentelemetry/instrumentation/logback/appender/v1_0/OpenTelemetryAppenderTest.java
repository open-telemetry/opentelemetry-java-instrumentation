/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
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
    OpenTelemetryAppender.install(openTelemetrySdk);
  }

  @Test
  void logWithSpan() { // Does not work for log replay but it is not likely to occur because
    // the log replay is related to the case where an OpenTelemetry object is not yet available
    // at the time the log is executed (and if no OpenTelemetry is available, the context
    // propagation can't happen)
    Span span1 = runWithSpan("span1", () -> logger.info("log message 1"));

    logger.info("log message 2");

    executeAfterLogsExecution();

    Span span2 = runWithSpan("span2", () -> logger.info("log message 3"));

    List<LogRecordData> logDataList = logRecordExporter.getFinishedLogRecordItems();
    assertThat(logDataList).hasSize(3);
    assertThat(logDataList.get(0)).hasSpanContext(span1.getSpanContext());
    assertThat(logDataList.get(1)).hasSpanContext(SpanContext.getInvalid());
    assertThat(logDataList.get(2)).hasSpanContext(span2.getSpanContext());
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
