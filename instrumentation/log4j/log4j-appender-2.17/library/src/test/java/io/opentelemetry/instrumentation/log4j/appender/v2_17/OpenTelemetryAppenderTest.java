/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OpenTelemetryAppenderTest extends AbstractOpenTelemetryAppenderTest {

  @RegisterExtension
  private static final LibraryInstrumentationExtension testing =
      LibraryInstrumentationExtension.create();

  @BeforeEach
  void setup() {
    generalBeforeEachSetup();
    OpenTelemetryAppender.install(testing.getOpenTelemetry());
  }

  @Override
  protected InstrumentationExtension getTesting() {
    return testing;
  }

  @Test
  void logWithSpan() { // Does not work for log replay, but it is not likely to occur because
    // the log replay is related to the case where an OpenTelemetry object is not yet available
    // at the time the log is executed (and if no OpenTelemetry is available, the context
    // propagation can't happen)
    Span span1 =
        testing.runWithSpan(
            "span1",
            () -> {
              logger.info("log message");
              return Span.current();
            });

    executeAfterLogsExecution();

    testing.waitAndAssertLogRecords(
        logRecord ->
            logRecord
                .hasSpanContext(span1.getSpanContext())
                .hasAttributesSatisfying(
                    equalTo(
                        ThreadIncubatingAttributes.THREAD_NAME, Thread.currentThread().getName()),
                    equalTo(ThreadIncubatingAttributes.THREAD_ID, Thread.currentThread().getId())));
  }
}
