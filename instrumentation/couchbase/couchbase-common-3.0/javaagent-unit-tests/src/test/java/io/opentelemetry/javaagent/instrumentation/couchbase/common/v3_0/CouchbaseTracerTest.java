/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.couchbase.common.v3_0;

import static io.opentelemetry.api.trace.SpanKind.CLIENT;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.DbAttributes.DB_SYSTEM_NAME;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;

import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CouchbaseTracerTest {

  private InMemorySpanExporter exporter;
  private InMemoryLogRecordExporter logExporter;
  private SdkTracerProvider tracerProvider;
  private SdkLoggerProvider loggerProvider;
  private Tracer tracer;
  private Logger exceptionLogger;

  @BeforeEach
  void setUp() {
    exporter = InMemorySpanExporter.create();
    logExporter = InMemoryLogRecordExporter.create();
    tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(exporter)).build();
    loggerProvider =
        SdkLoggerProvider.builder()
            .addLogRecordProcessor(SimpleLogRecordProcessor.create(logExporter))
            .build();
    OpenTelemetrySdk openTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .setLoggerProvider(loggerProvider)
            .build();
    tracer = openTelemetry.getTracer("test");
    exceptionLogger = openTelemetry.getLogsBridge().loggerBuilder("test").build();
  }

  @AfterEach
  void tearDown() {
    tracerProvider.close();
    loggerProvider.close();
  }

  @Test
  void createsRootSpanWhenCurrentContextIsDisabled() {
    Span current = tracer.spanBuilder("current").startSpan();
    try (Scope ignored = current.makeCurrent()) {
      CouchbaseSpan span =
          new CouchbaseTracer(tracer, exceptionLogger, false, false, true)
              .startSpan("request", null);
      span.end();
    }
    current.end();

    SpanData request = findSpan("request");
    assertThat(request.getParentSpanContext().isValid()).isFalse();
  }

  @Test
  void inheritsCurrentContextWhenEnabled() {
    Span current = tracer.spanBuilder("current").startSpan();
    try (Scope ignored = current.makeCurrent()) {
      CouchbaseSpan span =
          new CouchbaseTracer(tracer, exceptionLogger, true, false, true)
              .startSpan("request", null);
      span.end();
    }
    current.end();

    assertThat(findSpan("request").getParentSpanId())
        .isEqualTo(current.getSpanContext().getSpanId());
  }

  @Test
  void explicitCouchbaseParentTakesPrecedence() {
    CouchbaseTracer couchbaseTracer =
        new CouchbaseTracer(tracer, exceptionLogger, true, false, true);
    CouchbaseSpan parent = couchbaseTracer.startSpan("parent", null);
    Span current = tracer.spanBuilder("current").startSpan();
    try (Scope ignored = current.makeCurrent()) {
      CouchbaseSpan child = couchbaseTracer.startSpan("child", parent);
      child.end();
    }
    current.end();
    parent.end();

    assertThat(findSpan("child").getParentSpanId())
        .isEqualTo(parent.getSpan().getSpanContext().getSpanId());
  }

  @Test
  void recordsSpanData() {
    CouchbaseSpan span =
        new CouchbaseTracer(tracer, exceptionLogger, false, false, true).startSpan("get", null);
    span.setAttribute("db.system", "couchbase");
    span.setAttribute("db.couchbase.retries", 2L);
    span.addEvent("dispatched", Instant.EPOCH);
    span.setStatus(StatusCode.ERROR);
    span.recordException(new IllegalStateException("failure"));
    span.end();

    SpanData spanData = findSpan("get");
    assertThat(spanData.getKind()).isEqualTo(CLIENT);
    assertThat(spanData.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
    assertThat(spanData).hasAttributesSatisfyingExactly(equalTo(DB_SYSTEM_NAME, "couchbase"));
    assertThat(spanData.getEvents()).extracting(event -> event.getName()).contains("dispatched");
    if (emitExceptionAsSpanEvents()) {
      assertThat(spanData.getEvents()).extracting(event -> event.getName()).contains("exception");
    } else {
      assertThat(spanData.getEvents())
          .extracting(event -> event.getName())
          .doesNotContain("exception");
    }
  }

  @Test
  void recordsExceptionAsConfiguredSignalWithCouchbaseCorrelation() {
    Span current = tracer.spanBuilder("current").startSpan();
    CouchbaseSpan span;
    IllegalStateException exception = new IllegalStateException("failure");
    try (Scope ignored = current.makeCurrent()) {
      span =
          new CouchbaseTracer(tracer, exceptionLogger, false, false, true).startSpan("get", null);
      span.recordException(exception);
    }
    span.end();
    current.end();

    SpanData spanData = findSpan("get");
    if (emitExceptionAsSpanEvents()) {
      assertThat(spanData.getEvents())
          .filteredOn(event -> event.getName().equals("exception"))
          .satisfiesExactly(
              event ->
                  assertThat(event)
                      .hasAttributesSatisfyingExactly(
                          equalTo(EXCEPTION_TYPE, IllegalStateException.class.getName()),
                          equalTo(EXCEPTION_MESSAGE, "failure"),
                          satisfies(EXCEPTION_STACKTRACE, val -> val.isNotNull())));
    } else {
      assertThat(spanData.getEvents())
          .filteredOn(event -> event.getName().equals("exception"))
          .isEmpty();
    }

    List<LogRecordData> logs = logExporter.getFinishedLogRecordItems();
    if (emitExceptionAsLogs()) {
      assertThat(logs)
          .satisfiesExactly(
              log ->
                  assertThat(log)
                      .hasSeverity(Severity.WARN)
                      .hasEventName("db.client.operation.exception")
                      .hasAttributesSatisfyingExactly(
                          equalTo(EXCEPTION_TYPE, IllegalStateException.class.getName()),
                          equalTo(EXCEPTION_MESSAGE, "failure"),
                          satisfies(EXCEPTION_STACKTRACE, val -> val.isNotNull())));
      assertThat(logs.get(0).getSpanContext().getTraceId()).isEqualTo(spanData.getTraceId());
      assertThat(logs.get(0).getSpanContext().getSpanId()).isEqualTo(spanData.getSpanId());
    } else {
      assertThat(logs).isEmpty();
    }
  }

  @Test
  void updatesSpanName() {
    CouchbaseSpan span =
        new CouchbaseTracer(tracer, exceptionLogger, false, false, true).startSpan("get", null);
    span.updateName("get _default");
    span.end();

    assertThat(findSpan("get _default").getName()).isEqualTo("get _default");
  }

  @Test
  void suppressesKnownSdkDetailSpansByDefault() {
    CouchbaseSpan span =
        new CouchbaseTracer(tracer, exceptionLogger, false, false, true)
            .startSpan("dispatch_to_server", null);
    span.recordException(new IllegalStateException("failure"));
    span.end();

    assertThat(exporter.getFinishedSpanItems()).isEmpty();
    assertThat(logExporter.getFinishedLogRecordItems()).isEmpty();
  }

  private SpanData findSpan(String name) {
    List<SpanData> spans = exporter.getFinishedSpanItems();
    return spans.stream()
        .filter(span -> span.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing span: " + name));
  }
}
