/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ExceptionRecorderTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  @Test
  void repeatedReportsAreEmittedIndependently() {
    Span span = otelTesting.getOpenTelemetry().getTracer("test").spanBuilder("span").startSpan();
    Context context = Context.root().with(span);
    Logger logger = otelTesting.getOpenTelemetry().getLogsBridge().loggerBuilder("test").build();
    InternalExceptionEventExtractor<String> extractor =
        (logRecordBuilder, unused, request) -> logRecordBuilder.setEventName(request);
    RuntimeException error = new RuntimeException("test");

    ExceptionRecorder.recordException(context, span, error, logger, extractor, "exception", null);
    ExceptionRecorder.recordException(context, span, error, logger, extractor, "exception", null);
    span.end();

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    spanData -> {
                      if (emitExceptionAsSpanEvents()) {
                        spanData.hasEventsSatisfyingExactly(
                            event -> event.hasName("exception"),
                            event -> event.hasName("exception"));
                      } else {
                        spanData.hasEventsSatisfyingExactly();
                      }
                    }));

    List<LogRecordData> logs = otelTesting.getLogRecords();
    if (emitExceptionAsLogs()) {
      assertThat(logs)
          .hasSize(2)
          .allSatisfy(
              log ->
                  assertThat(log).hasEventName("exception").hasSpanContext(span.getSpanContext()));
    } else {
      assertThat(logs).isEmpty();
    }
  }
}
