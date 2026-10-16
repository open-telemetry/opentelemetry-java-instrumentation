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

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import java.util.concurrent.CompletionException;
import java.util.stream.IntStream;
import org.assertj.core.api.AbstractAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ExceptionRecorderTest {
  @RegisterExtension static final OpenTelemetryExtension testing = OpenTelemetryExtension.create();

  @Test
  void usesOwnerScopeAndExtractorAndDeduplicatesTerminalError() {
    InstrumenterBuilder<String, Void> builder =
        Instrumenter.<String, Void>builder(testing.getOpenTelemetry(), "owner", unused -> "request")
            .setInstrumentationVersion("1.2.3")
            .setSchemaUrl("https://opentelemetry.io/schemas/1.30.0")
            .setErrorCauseExtractor(
                error -> error instanceof CompletionException ? error.getCause() : error);
    Experimental.setExceptionEventExtractor(
        builder,
        (log, context, request) ->
            log.setEventName("http.server.request.exception")
                .setSeverity(Severity.ERROR)
                .setAttribute(stringKey("request"), request));
    Instrumenter<String, Void> owner = builder.buildInstrumenter(SpanKindExtractor.alwaysServer());
    Context context = owner.start(Context.root(), "original request");
    IllegalStateException error = new IllegalStateException("failure");

    ExceptionRecorder.recordException(context, new CompletionException(error));
    ExceptionRecorder.recordException(context, error);
    owner.end(context, "original request", null, new CompletionException(error));

    InstrumentationScopeInfo scope =
        InstrumentationScopeInfo.builder("owner")
            .setVersion("1.2.3")
            .setSchemaUrl("https://opentelemetry.io/schemas/1.30.0")
            .build();
    assertThat(testing.getSpans())
        .singleElement()
        .satisfies(
            span ->
                assertThat(span)
                    .hasInstrumentationScopeInfo(scope)
                    .hasStatus(StatusData.error())
                    .hasException(emitExceptionAsSpanEvents() ? error : null));
    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 1 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(testing.getLogRecords().get(0).getInstrumentationScopeInfo()).isEqualTo(scope);
      assertThat(testing.getLogRecords().get(0))
          .hasSpanContext(Span.fromContext(context).getSpanContext())
          .hasEventName("http.server.request.exception")
          .hasSeverity(Severity.ERROR)
          .hasAttributesSatisfyingExactly(
              equalTo(stringKey("request"), "original request"),
              equalTo(EXCEPTION_TYPE, IllegalStateException.class.getName()),
              equalTo(EXCEPTION_MESSAGE, "failure"),
              satisfies(EXCEPTION_STACKTRACE, AbstractAssert::isNotNull));
    }
  }

  @ParameterizedTest
  @EnumSource(SpanKind.class)
  void usesOwnerDefaultEventAndSeverity(SpanKind kind) {
    Instrumenter<String, Void> owner =
        Instrumenter.<String, Void>builder(testing.getOpenTelemetry(), "owner", unused -> "request")
            .buildInstrumenter(unused -> kind);
    Context context = owner.start(Context.root(), "request");
    IllegalArgumentException error = new IllegalArgumentException("failure");

    ExceptionRecorder.recordException(context, error);
    owner.end(context, "request", null, null);

    assertThat(testing.getSpans())
        .singleElement()
        .satisfies(
            span -> assertThat(span).hasException(emitExceptionAsSpanEvents() ? error : null));
    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 1 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(testing.getLogRecords().get(0))
          .hasEventName("exception")
          .hasSeverity(
              kind == SpanKind.SERVER || kind == SpanKind.CONSUMER ? Severity.ERROR : Severity.WARN)
          .hasSpanContext(Span.fromContext(context).getSpanContext());
    }
  }

  @Test
  void distinctErrorsAreNotDeduplicatedAndStateIsPerSpan() {
    Instrumenter<String, Void> owner =
        Instrumenter.<String, Void>builder(testing.getOpenTelemetry(), "owner", unused -> "request")
            .buildInstrumenter();
    IllegalStateException first = new IllegalStateException("same message");
    IllegalStateException second = new IllegalStateException("same message");
    Context firstContext = owner.start(Context.root(), "first");
    Context secondContext = owner.start(Context.root(), "second");

    ExceptionRecorder.recordException(firstContext, first);
    ExceptionRecorder.recordException(firstContext, second);
    ExceptionRecorder.recordException(firstContext, first);
    ExceptionRecorder.recordException(secondContext, first);
    owner.end(firstContext, "first", null, first);
    owner.end(secondContext, "second", null, first);

    assertThat(testing.getSpans()).hasSize(2);
    assertThat(testing.getSpans().get(0).getEvents()).hasSize(emitExceptionAsSpanEvents() ? 2 : 0);
    assertThat(testing.getSpans().get(1).getEvents()).hasSize(emitExceptionAsSpanEvents() ? 1 : 0);
    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 3 : 0);
  }

  @Test
  void emitsLogsForNonRecordingOwnerAndDeduplicates() {
    try (SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder().setSampler(Sampler.alwaysOff()).build()) {
      OpenTelemetrySdk telemetry =
          OpenTelemetrySdk.builder()
              .setTracerProvider(tracerProvider)
              .setLoggerProvider(
                  ((OpenTelemetrySdk) testing.getOpenTelemetry()).getSdkLoggerProvider())
              .build();
      Instrumenter<String, Void> owner =
          Instrumenter.<String, Void>builder(telemetry, "unsampled-owner", unused -> "request")
              .buildInstrumenter(SpanKindExtractor.alwaysServer());
      Context context = owner.start(Context.root(), "request");
      IllegalStateException error = new IllegalStateException("failure");

      assertThat(Span.fromContext(context).isRecording()).isFalse();
      ExceptionRecorder.recordException(context, error);
      ExceptionRecorder.recordException(context, error);
      owner.end(context, "request", null, error);

      assertThat(testing.getSpans()).isEmpty();
      assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 1 : 0);
      if (emitExceptionAsLogs()) {
        assertThat(testing.getLogRecords().get(0).getInstrumentationScopeInfo())
            .isEqualTo(InstrumentationScopeInfo.create("unsampled-owner"));
        assertThat(testing.getLogRecords().get(0))
            .hasSpanContext(Span.fromContext(context).getSpanContext());
      }
    }
  }

  @Test
  void suppressedInstrumentationHandsOffToExistingOwner() {
    InstrumenterBuilder<String, Void> ownerBuilder =
        Instrumenter.builder(testing.getOpenTelemetry(), "owner", unused -> "request");
    Experimental.setSpanSuppressionStrategy(ownerBuilder, "span-kind");
    Instrumenter<String, Void> owner =
        ownerBuilder.buildInstrumenter(SpanKindExtractor.alwaysServer());
    InstrumenterBuilder<String, Void> suppressedBuilder =
        Instrumenter.<String, Void>builder(
            testing.getOpenTelemetry(), "suppressed", unused -> "request");
    Experimental.setSpanSuppressionStrategy(suppressedBuilder, "span-kind");
    Instrumenter<String, Void> suppressed =
        suppressedBuilder.buildInstrumenter(SpanKindExtractor.alwaysServer());
    Context context = owner.start(Context.root(), "request");
    IllegalStateException error = new IllegalStateException("failure");

    assertThat(suppressed.shouldStart(context, "request")).isFalse();
    ExceptionRecorder.recordException(context, error);
    owner.end(context, "request", null, error);

    assertThat(testing.getSpans()).hasSize(1);
    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 1 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(testing.getLogRecords().get(0).getInstrumentationScopeInfo())
          .isEqualTo(InstrumentationScopeInfo.create("owner"));
    }
  }

  @Test
  void doesNotUseInheritedOwnerForAnUnrelatedCurrentSpan() {
    Instrumenter<String, Void> owner =
        Instrumenter.<String, Void>builder(testing.getOpenTelemetry(), "owner", unused -> "request")
            .buildInstrumenter();
    Context context = owner.start(Context.root(), "request");
    Span child =
        testing
            .getOpenTelemetry()
            .getTracer("manual")
            .spanBuilder("child")
            .setParent(context)
            .startSpan();
    IllegalStateException error = new IllegalStateException("failure");

    ExceptionRecorder.recordException(context.with(child), error);
    ExceptionRecorder.recordException(context.with(Span.getInvalid()), error);
    ExceptionRecorder.recordException(Context.root(), error);
    child.end();
    owner.end(context, "request", null, null);

    assertThat(testing.getSpans()).hasSize(2);
    assertThat(testing.getSpans().get(0))
        .hasName("child")
        .hasException(emitExceptionAsSpanEvents() ? error : null);
    assertThat(testing.getSpans().get(1)).hasName("request").hasException(null);
    assertThat(testing.getLogRecords()).isEmpty();
  }

  @Test
  void doesNotEmitAfterOwnerEnds() {
    Instrumenter<String, Void> owner =
        Instrumenter.<String, Void>builder(testing.getOpenTelemetry(), "owner", unused -> "request")
            .buildInstrumenter();
    Context context = owner.start(Context.root(), "request");
    owner.end(context, "request", null, null);

    ExceptionRecorder.recordException(context, new IllegalStateException("late failure"));

    assertThat(testing.getSpans())
        .singleElement()
        .satisfies(span -> assertThat(span).hasException(null));
    assertThat(testing.getLogRecords()).isEmpty();
  }

  @Test
  void concurrentReportsRecordTheSameErrorOnlyOnce() {
    Instrumenter<String, Void> owner =
        Instrumenter.<String, Void>builder(testing.getOpenTelemetry(), "owner", unused -> "request")
            .buildInstrumenter();
    Context context = owner.start(Context.root(), "request");
    IllegalStateException error = new IllegalStateException("failure");

    IntStream.range(0, 32)
        .parallel()
        .forEach(unused -> ExceptionRecorder.recordException(context, error));
    owner.end(context, "request", null, error);

    assertThat(testing.getSpans())
        .singleElement()
        .satisfies(
            span -> assertThat(span).hasException(emitExceptionAsSpanEvents() ? error : null));
    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 1 : 0);
  }

  @Test
  void canTargetServerOwnerFromNestedInstrumenterContext() {
    Instrumenter<String, Void> server =
        Instrumenter.<String, Void>builder(testing.getOpenTelemetry(), "server", unused -> "server")
            .buildInstrumenter(SpanKindExtractor.alwaysServer());
    Instrumenter<String, Void> controller =
        Instrumenter.<String, Void>builder(
                testing.getOpenTelemetry(), "controller", unused -> "controller")
            .buildInstrumenter();
    Context serverContext = server.start(Context.root(), "request");
    Context controllerContext = controller.start(serverContext, "request");
    IllegalStateException error = new IllegalStateException("failure");

    Context targetContext = controllerContext.with(Span.fromContext(serverContext));
    ExceptionRecorder.recordException(targetContext, error);
    ExceptionRecorder.recordException(targetContext, error);
    controller.end(controllerContext, "request", null, null);
    server.end(serverContext, "request", null, error);

    assertThat(testing.getSpans()).hasSize(2);
    assertThat(testing.getSpans().get(0)).hasName("controller").hasException(null);
    assertThat(testing.getSpans().get(1))
        .hasName("server")
        .hasException(emitExceptionAsSpanEvents() ? error : null);
    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 1 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(testing.getLogRecords().get(0).getInstrumentationScopeInfo())
          .isEqualTo(InstrumentationScopeInfo.create("server"));
      assertThat(testing.getLogRecords().get(0))
          .hasSpanContext(Span.fromContext(serverContext).getSpanContext())
          .hasSeverity(Severity.ERROR);
    }
  }

  @Test
  void preservesControllerEventTargetButUsesServerLogOwner() {
    Instrumenter<String, Void> server =
        Instrumenter.<String, Void>builder(testing.getOpenTelemetry(), "server", unused -> "server")
            .buildInstrumenter(SpanKindExtractor.alwaysServer());
    Instrumenter<String, Void> controller =
        Instrumenter.<String, Void>builder(
                testing.getOpenTelemetry(), "controller", unused -> "controller")
            .buildInstrumenter();
    Context serverContext = server.start(Context.root(), "request");
    Context controllerContext = controller.start(serverContext, "request");
    Span controllerSpan = Span.fromContext(controllerContext);
    IllegalStateException error = new IllegalStateException("failure");

    Context ownerContext = controllerContext.with(Span.fromContext(serverContext));
    ExceptionRecorder.recordException(ownerContext, controllerSpan, error);
    ExceptionRecorder.recordException(ownerContext, controllerSpan, error);
    controller.end(controllerContext, "request", null, null);
    server.end(serverContext, "request", null, error);

    assertThat(testing.getSpans()).hasSize(2);
    assertThat(testing.getSpans().get(0))
        .hasName("controller")
        .hasException(emitExceptionAsSpanEvents() ? error : null);
    assertThat(testing.getSpans().get(1))
        .hasName("server")
        .hasException(null)
        .hasStatus(StatusData.error());
    assertThat(testing.getLogRecords()).hasSize(emitExceptionAsLogs() ? 1 : 0);
    if (emitExceptionAsLogs()) {
      assertThat(testing.getLogRecords().get(0).getInstrumentationScopeInfo())
          .isEqualTo(InstrumentationScopeInfo.create("server"));
      assertThat(testing.getLogRecords().get(0))
          .hasSpanContext(Span.fromContext(serverContext).getSpanContext())
          .hasSeverity(Severity.ERROR);
    }
  }
}
