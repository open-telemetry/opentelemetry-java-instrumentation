/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanId;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceId;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.semconv.SchemaUrls;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ExceptionRecorderTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  @Test
  void recordException_usesSelectedSpanAndEndTime() {
    ExceptionRecorder<String> recorder = createRecorder();
    Span selectedSpan =
        otelTesting.getOpenTelemetry().getTracer("test").spanBuilder("selected").startSpan();
    SpanContext selectedSpanContext = selectedSpan.getSpanContext();
    Span currentSpan =
        Span.wrap(
            SpanContext.create(
                TraceId.fromLongs(1, 2),
                SpanId.fromLong(3),
                TraceFlags.getSampled(),
                TraceState.getDefault()));
    Instant timestamp = Instant.ofEpochSecond(123, 456);
    IllegalStateException exception = new IllegalStateException("test");

    recorder.recordException(
        Context.root().with(currentSpan), selectedSpan, exception, "request", timestamp);
    selectedSpan.end();

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasName("selected")
                            .hasException(emitExceptionAsSpanEvents() ? exception : null)));

    List<LogRecordData> logs = otelTesting.getLogRecords();
    assertThat(logs).hasSize(emitExceptionAsLogs() ? 1 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(logs.get(0))
          .hasEventName("request.exception")
          .hasSeverity(Severity.ERROR)
          .hasSpanContext(selectedSpanContext);
      assertThat(logs.get(0).getTimestampEpochNanos()).isEqualTo(123_000_000_456L);
      assertThat(logs.get(0).getInstrumentationScopeInfo())
          .isEqualTo(
              InstrumentationScopeInfo.builder("test")
                  .setVersion("1.2.3")
                  .setSchemaUrl(SchemaUrls.V1_41_0)
                  .build());
    }
  }

  @Test
  void recordException_emitsLogForNonRecordingSpan() {
    ExceptionRecorder<String> recorder = createRecorder();
    SpanContext spanContext =
        SpanContext.create(
            TraceId.fromLongs(1, 2),
            SpanId.fromLong(3),
            TraceFlags.getDefault(),
            TraceState.getDefault());
    Span nonRecordingSpan = Span.wrap(spanContext);

    recorder.recordException(
        Context.root(), nonRecordingSpan, new IllegalStateException("test"), "request", null);

    assertThat(otelTesting.getLogRecords()).hasSize(emitExceptionAsLogs() ? 1 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(otelTesting.getLogRecords().get(0)).hasSpanContext(spanContext);
    }
  }

  private static ExceptionRecorder<String> createRecorder() {
    return ExceptionRecorder.create(
        otelTesting.getOpenTelemetry(),
        "test",
        "1.2.3",
        SchemaUrls.V1_41_0,
        (logRecordBuilder, context, request) -> {
          logRecordBuilder.setEventName(request + ".exception");
          logRecordBuilder.setSeverity(Severity.ERROR);
        });
  }
}
