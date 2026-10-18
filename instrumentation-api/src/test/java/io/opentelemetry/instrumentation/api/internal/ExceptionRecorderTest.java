/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_STACKTRACE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static java.util.Arrays.asList;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ErrorCauseExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.time.Instant;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ExceptionRecorderTest {
  @RegisterExtension static final OpenTelemetryExtension testing = OpenTelemetryExtension.create();

  @Test
  void frameworkScopeAndReportingContextAreIndependentOfSpanEventTarget() {
    Instrumenter<String, Void> server =
        Instrumenter.<String, Void>builder(testing.getOpenTelemetry(), "server", unused -> "server")
            .buildInstrumenter(SpanKindExtractor.alwaysServer());
    Instrumenter<String, Void> controller =
        Instrumenter.<String, Void>builder(
                testing.getOpenTelemetry(), "controller", unused -> "controller")
            .buildInstrumenter();
    Context serverContext = server.start(Context.root(), "request");
    Context controllerContext = controller.start(serverContext, "request");
    Logger logger =
        testing
            .getOpenTelemetry()
            .getLogsBridge()
            .loggerBuilder("framework")
            .setInstrumentationVersion("1.2.3")
            .setSchemaUrl("https://opentelemetry.io/schemas/1.30.0")
            .build();
    IllegalStateException error = new IllegalStateException("failure");

    try (Scope ignored = controllerContext.makeCurrent()) {
      ExceptionRecorder.recordServerException(
          Context.current(),
          Span.fromContext(serverContext),
          error,
          ErrorCauseExtractor.getDefault(),
          logger);
    }

    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 1 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(testing.getLogRecords().get(0).getInstrumentationScopeInfo())
          .isEqualTo(
              InstrumentationScopeInfo.builder("framework")
                  .setVersion("1.2.3")
                  .setSchemaUrl("https://opentelemetry.io/schemas/1.30.0")
                  .build());
      assertThat(testing.getLogRecords().get(0))
          .hasSpanContext(Span.fromContext(controllerContext).getSpanContext())
          .hasEventName("http.server.request.exception")
          .hasSeverity(Severity.ERROR)
          .hasAttributesSatisfyingExactly(
              equalTo(EXCEPTION_TYPE, IllegalStateException.class.getName()),
              equalTo(EXCEPTION_MESSAGE, "failure"),
              satisfies(EXCEPTION_STACKTRACE, AbstractAssert::isNotNull));
    }

    controller.end(controllerContext, "request", null, null);
    server.end(serverContext, "request", null, null);
    assertThat(testing.getSpans()).hasSize(2);
    assertThat(testing.getSpans().get(0)).hasName("controller").hasException(null);
    assertThat(testing.getSpans().get(1))
        .hasName("server")
        .hasStatus(StatusData.unset())
        .hasException(emitExceptionAsSpanEvents() ? error : null);
  }

  @ParameterizedTest
  @EnumSource(SpanKind.class)
  void ordinaryExceptionsStayWithTheirOperation(SpanKind kind) {
    Instrumenter<String, Void> server =
        Instrumenter.<String, Void>builder(testing.getOpenTelemetry(), "server", unused -> "server")
            .buildInstrumenter(SpanKindExtractor.alwaysServer());
    Context serverContext = server.start(Context.root(), "request");
    Instrumenter<String, Void> instrumenter =
        Instrumenter.<String, Void>builder(
                testing.getOpenTelemetry(), "operation", unused -> "request")
            .buildInstrumenter(unused -> kind);
    Context context = instrumenter.start(serverContext, "request");
    IllegalArgumentException error = new IllegalArgumentException("failure");

    instrumenter.end(context, "request", null, error);
    server.end(serverContext, "request", null, null);

    assertThat(testing.getSpans()).hasSize(2);
    assertThat(testing.getSpans().get(0))
        .hasName("request")
        .hasStatus(StatusData.error())
        .hasException(emitExceptionAsSpanEvents() ? error : null);
    assertThat(testing.getSpans().get(1)).hasName("server").hasException(null);
    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 1 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(testing.getLogRecords().get(0).getInstrumentationScopeInfo())
          .isEqualTo(InstrumentationScopeInfo.create("operation"));
      assertThat(testing.getLogRecords().get(0))
          .hasEventName("exception")
          .hasSeverity(
              kind == SpanKind.SERVER || kind == SpanKind.CONSUMER ? Severity.ERROR : Severity.WARN)
          .hasSpanContext(Span.fromContext(context).getSpanContext());
    }
  }

  @Test
  void instrumenterRetainsExtractionCustomAttributesAndExplicitTimestamp() {
    InstrumenterBuilder<String, Void> builder =
        Instrumenter.<String, Void>builder(
                testing.getOpenTelemetry(), "operation", unused -> "request")
            .setErrorCauseExtractor(
                error -> error instanceof CompletionException ? error.getCause() : error);
    Experimental.setExceptionEventExtractor(
        builder,
        (log, context, request) ->
            log.setEventName("custom.exception")
                .setSeverity(Severity.WARN)
                .setAttribute(stringKey("request"), request));
    Instrumenter<String, Void> instrumenter = builder.buildInstrumenter();
    IllegalStateException error = new IllegalStateException("failure");
    Instant start = Instant.parse("2020-01-01T00:00:00Z");
    Instant end = start.plusSeconds(1);

    InstrumenterUtil.startAndEnd(
        instrumenter,
        Context.root(),
        "original request",
        null,
        new CompletionException(error),
        start,
        end);

    assertThat(testing.getSpans())
        .singleElement()
        .satisfies(
            span ->
                assertThat(span)
                    .hasStatus(StatusData.error())
                    .hasException(emitExceptionAsSpanEvents() ? error : null));
    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 1 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(testing.getLogRecords().get(0))
          .hasEventName("custom.exception")
          .hasSeverity(Severity.WARN)
          .hasAttributesSatisfyingExactly(
              equalTo(stringKey("request"), "original request"),
              equalTo(EXCEPTION_TYPE, IllegalStateException.class.getName()),
              equalTo(EXCEPTION_MESSAGE, "failure"),
              satisfies(EXCEPTION_STACKTRACE, AbstractAssert::isNotNull));
      assertThat(testing.getLogRecords().get(0).getTimestampEpochNanos())
          .isEqualTo(end.getEpochSecond() * 1_000_000_000L);
    }
  }

  @Test
  void frameworkLogsDoNotRequireLocalRootOrRecordingSpan() {
    Logger logger = ExceptionRecorder.createLogger(testing.getOpenTelemetry(), "framework", null);
    Span nonRecording =
        Span.wrap(
            SpanContext.create(
                "11111111111111111111111111111111",
                "1111111111111111",
                TraceFlags.getDefault(),
                TraceState.getDefault()));
    IllegalStateException error = new IllegalStateException("failure");

    ExceptionRecorder.recordServerException(
        Context.root(), null, error, ErrorCauseExtractor.getDefault(), logger);
    ExceptionRecorder.recordServerException(
        Context.root().with(nonRecording),
        nonRecording,
        error,
        ErrorCauseExtractor.getDefault(),
        logger);

    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 2 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(testing.getLogRecords().get(0)).hasSpanContext(Span.getInvalid().getSpanContext());
      assertThat(testing.getLogRecords().get(1)).hasSpanContext(nonRecording.getSpanContext());
    }
  }

  @Test
  void frameworkEmitsImmediatelyWithReportTimeTimestamp() {
    Instrumenter<String, Void> instrumenter =
        Instrumenter.<String, Void>builder(
                testing.getOpenTelemetry(), "server", unused -> "request")
            .buildInstrumenter(SpanKindExtractor.alwaysServer());
    Context context = instrumenter.start(Context.root(), "request");
    Logger logger = ExceptionRecorder.createLogger(testing.getOpenTelemetry(), "framework", null);
    IllegalStateException error = new IllegalStateException("failure");
    long before = epochNanos(Instant.now());

    ExceptionRecorder.recordServerException(
        context,
        Span.fromContext(context),
        new CompletionException(error),
        ErrorCauseExtractor.getDefault(),
        logger);

    long after = epochNanos(Instant.now());
    assertThat(testing.getSpans()).isEmpty();
    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 1 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(testing.getLogRecords().get(0).getTimestampEpochNanos()).isBetween(before, after);
      assertThat(testing.getLogRecords().get(0)).hasException(error);
    }
    instrumenter.end(context, "request", null, null);
    assertThat(testing.getSpans())
        .singleElement()
        .satisfies(
            span -> assertThat(span).hasException(emitExceptionAsSpanEvents() ? error : null));
  }

  @Test
  void repeatedReportsEmitEachSelectedSignal() {
    Instrumenter<String, Void> instrumenter =
        Instrumenter.<String, Void>builder(
                testing.getOpenTelemetry(), "server", unused -> "request")
            .buildInstrumenter(SpanKindExtractor.alwaysServer());
    Context context = instrumenter.start(Context.root(), "request");
    Span span = Span.fromContext(context);
    Logger logger = ExceptionRecorder.createLogger(testing.getOpenTelemetry(), "framework", null);
    IllegalStateException first = new IllegalStateException("first");
    IllegalStateException second = new IllegalStateException("second");

    for (Throwable reported : asList(new CompletionException(first), first, second)) {
      ExceptionRecorder.recordServerException(
          context, span, reported, ErrorCauseExtractor.getDefault(), logger);
    }
    instrumenter.end(context, "request", null, null);

    assertThat(testing.getSpans())
        .singleElement()
        .satisfies(
            spanData ->
                assertThat(spanData.getEvents()).hasSize(emitExceptionAsSpanEvents() ? 3 : 0));
    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 3 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(testing.getLogRecords().get(0).getAttributes().get(EXCEPTION_MESSAGE))
          .isEqualTo("first");
      assertThat(testing.getLogRecords().get(1).getAttributes().get(EXCEPTION_MESSAGE))
          .isEqualTo("first");
      assertThat(testing.getLogRecords().get(2).getAttributes().get(EXCEPTION_MESSAGE))
          .isEqualTo("second");
    }
  }

  @Test
  void serverCustomExtractorReplacesDefaultExtraction() {
    Instrumenter<String, Void> instrumenter =
        Instrumenter.<String, Void>builder(
                testing.getOpenTelemetry(), "server", unused -> "request")
            .buildInstrumenter(SpanKindExtractor.alwaysServer());
    Context context = instrumenter.start(Context.root(), "request");
    CompletionException extracted = new CompletionException(new IllegalStateException("cause"));
    IllegalArgumentException observed = new IllegalArgumentException("observed");
    AtomicInteger calls = new AtomicInteger();

    ExceptionRecorder.recordServerException(
        context,
        Span.fromContext(context),
        observed,
        error -> {
          assertThat(error).isSameAs(observed);
          calls.incrementAndGet();
          return extracted;
        },
        ExceptionRecorder.createLogger(testing.getOpenTelemetry(), "framework", null));
    instrumenter.end(context, "request", null, null);

    assertThat(calls.get()).isEqualTo(1);
    assertThat(testing.getSpans())
        .singleElement()
        .satisfies(
            span -> assertThat(span).hasException(emitExceptionAsSpanEvents() ? extracted : null));
    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 1 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(testing.getLogRecords().get(0)).hasException(extracted);
    }
  }

  @Test
  void instrumenterCustomExtractorResultIsUsedForEmissionAttributesAndStatus() {
    CompletionException extracted = new CompletionException(new IllegalStateException("cause"));
    IllegalArgumentException observed = new IllegalArgumentException("observed");
    AtomicInteger calls = new AtomicInteger();
    Instrumenter<String, Void> instrumenter =
        Instrumenter.<String, Void>builder(
                testing.getOpenTelemetry(), "operation", unused -> "request")
            .setErrorCauseExtractor(
                error -> {
                  assertThat(error).isSameAs(observed);
                  calls.incrementAndGet();
                  return extracted;
                })
            .addAttributesExtractor(
                new AttributesExtractor<String, Void>() {
                  @Override
                  public void onStart(
                      AttributesBuilder attributes, Context parentContext, String request) {}

                  @Override
                  public void onEnd(
                      AttributesBuilder attributes,
                      Context context,
                      String request,
                      Void response,
                      Throwable error) {
                    assertThat(error).isSameAs(extracted);
                  }
                })
            .setSpanStatusExtractor(
                (status, request, response, error) -> {
                  assertThat(error).isSameAs(extracted);
                  status.setStatus(StatusCode.ERROR);
                })
            .buildInstrumenter();
    Context context = instrumenter.start(Context.root(), "request");

    instrumenter.end(context, "request", null, observed);

    assertThat(calls.get()).isEqualTo(1);
    assertThat(testing.getSpans())
        .singleElement()
        .satisfies(
            span ->
                assertThat(span)
                    .hasStatus(StatusData.error())
                    .hasException(emitExceptionAsSpanEvents() ? extracted : null));
    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 1 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(testing.getLogRecords().get(0)).hasException(extracted);
    }
  }

  private static long epochNanos(Instant instant) {
    return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
  }
}
