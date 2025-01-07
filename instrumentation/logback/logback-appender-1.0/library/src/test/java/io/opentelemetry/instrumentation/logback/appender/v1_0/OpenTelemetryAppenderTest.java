/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OpenTelemetryAppenderTest extends AbstractOpenTelemetryAppenderTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @BeforeEach
  void setup() {
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Test
  void logWithSpan() { // Does not work for log replay but it is not likely to occur because
    // the log replay is related to the case where an OpenTelemetry object is not yet available
    // at the time the log is executed (and if no OpenTelemetry is available, the context
    // propagation can't happen)
    Span span1 =
        testing.runWithSpan(
            "span1",
            () -> {
              logger.info("log message 1");
              return Span.current();
            });

    logger.info("log message 2");

    executeAfterLogsExecution();

    Span span2 =
        testing.runWithSpan(
            "span2",
            () -> {
              logger.info("log message 3");
              return Span.current();
            });

    testing.waitAndAssertLogRecords(
        logRecord -> logRecord.hasSpanContext(span1.getSpanContext()),
        logRecord -> logRecord.hasSpanContext(SpanContext.getInvalid()),
        logRecord -> logRecord.hasSpanContext(span2.getSpanContext()));
  }
}
