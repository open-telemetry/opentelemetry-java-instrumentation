/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.graphql.common.v12_0.internal;

import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsLogs;
import static io.opentelemetry.instrumentation.api.internal.SemconvExceptionSignal.emitExceptionAsSpanEvents;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import graphql.ErrorType;
import graphql.ExceptionWhileDataFetching;
import graphql.GraphQLError;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TracerBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.opentelemetry.semconv.SchemaUrls;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GraphqlErrorRecorderTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.graphql-java-common-12.0";

  @ParameterizedTest
  @MethodSource("recordingCases")
  void recordsError(boolean sampled, String schemaUrl, String message) {
    GraphQLError error = mock(GraphQLError.class);
    when(error.getErrorType()).thenReturn(ErrorType.ValidationError);
    when(error.getMessage()).thenReturn(message);

    assertRecordedError(
        INSTRUMENTATION_NAME, sampled, schemaUrl, error, "ValidationError", message);
  }

  private static Stream<Arguments> recordingCases() {
    return Stream.of(true, false)
        .flatMap(
            sampled ->
                Stream.of(SchemaUrls.V1_44_0, null)
                    .flatMap(
                        schemaUrl ->
                            Stream.of("invalid field", "", null)
                                .map(message -> Arguments.of(sampled, schemaUrl, message))));
  }

  @Test
  void preservesStructuredErrorInsteadOfUnwrappingThrowable() {
    ExceptionWhileDataFetching error = mock(ExceptionWhileDataFetching.class);
    when(error.getErrorType()).thenReturn(ErrorType.DataFetchingException);
    when(error.getMessage()).thenReturn("Exception while fetching data (/field) : failure");

    assertRecordedError(
        INSTRUMENTATION_NAME,
        true,
        SchemaUrls.V1_44_0,
        error,
        "DataFetchingException",
        "Exception while fetching data (/field) : failure");
  }

  @Test
  void recordsNullErrorTypeAndMissingInstrumentationVersion() {
    GraphQLError error = mock(GraphQLError.class);
    when(error.getMessage()).thenReturn("failure");

    assertRecordedError("unversioned-graphql", true, null, error, "null", "failure");
  }

  private static void assertRecordedError(
      String instrumentationName,
      boolean sampled,
      String schemaUrl,
      GraphQLError error,
      String expectedType,
      String expectedMessage) {
    InMemorySpanExporter spans = InMemorySpanExporter.create();
    InMemoryLogRecordExporter logs = InMemoryLogRecordExporter.create();
    try (OpenTelemetrySdk openTelemetry =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .setSampler(sampled ? Sampler.alwaysOn() : Sampler.alwaysOff())
                    .addSpanProcessor(SimpleSpanProcessor.create(spans))
                    .build())
            .setLoggerProvider(
                SdkLoggerProvider.builder()
                    .addLogRecordProcessor(SimpleLogRecordProcessor.create(logs))
                    .build())
            .build()) {
      String version = EmbeddedInstrumentationProperties.findVersion(instrumentationName);
      if (instrumentationName.equals(INSTRUMENTATION_NAME)) {
        assertThat(version).isNotEmpty();
      } else {
        assertThat(version).isNull();
      }
      InstrumentationScopeInfo expectedScope =
          InstrumentationScopeInfo.builder(instrumentationName)
              .setVersion(version)
              .setSchemaUrl(schemaUrl)
              .build();
      TracerBuilder tracerBuilder = openTelemetry.tracerBuilder(instrumentationName);
      if (version != null) {
        tracerBuilder.setInstrumentationVersion(version);
      }
      if (schemaUrl != null) {
        tracerBuilder.setSchemaUrl(schemaUrl);
      }
      Span span =
          tracerBuilder
              .build()
              .spanBuilder("GraphQL Operation")
              .setNoParent()
              .setSpanKind(SpanKind.INTERNAL)
              .startSpan();
      Context context = Context.root().with(span);
      assertThat(span.getSpanContext().isValid()).isTrue();
      assertThat(span.isRecording()).isEqualTo(sampled);

      // Completion callbacks may run with an unrelated current context.
      try (Scope ignored = Context.root().makeCurrent()) {
        new GraphqlErrorRecorder(openTelemetry, instrumentationName, schemaUrl)
            .record(context, error);
      }
      span.end();

      if (sampled) {
        assertThat(spans.getFinishedSpanItems())
            .singleElement()
            .satisfies(
                spanData -> {
                  assertThat(spanData).hasInstrumentationScopeInfo(expectedScope);
                  if (emitExceptionAsSpanEvents()) {
                    assertThat(spanData.getEvents())
                        .singleElement()
                        .satisfies(
                            event ->
                                assertThat(event)
                                    .hasName("exception")
                                    .hasAttributesSatisfyingExactly(
                                        equalTo(EXCEPTION_TYPE, expectedType),
                                        equalTo(EXCEPTION_MESSAGE, expectedMessage)));
                  } else {
                    assertThat(spanData.getEvents()).isEmpty();
                  }
                });
      } else {
        assertThat(spans.getFinishedSpanItems()).isEmpty();
      }
      if (emitExceptionAsLogs()) {
        assertThat(logs.getFinishedLogRecordItems())
            .singleElement()
            .satisfies(
                log -> {
                  assertThat(log)
                      .hasEventName("exception")
                      .hasSeverity(Severity.WARN)
                      .hasSpanContext(span.getSpanContext())
                      .hasInstrumentationScope(expectedScope)
                      .hasAttributesSatisfyingExactly(
                          equalTo(EXCEPTION_TYPE, expectedType),
                          equalTo(EXCEPTION_MESSAGE, expectedMessage));
                  assertThat(log.getBodyValue()).isNull();
                  assertThat(log.getSeverityText()).isNull();
                });
      } else {
        assertThat(logs.getFinishedLogRecordItems()).isEmpty();
      }
    }
  }
}
