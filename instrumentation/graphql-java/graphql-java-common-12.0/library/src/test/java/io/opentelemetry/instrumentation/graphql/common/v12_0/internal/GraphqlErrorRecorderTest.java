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
import graphql.GraphQLError;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
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
import io.opentelemetry.semconv.SchemaUrls;
import org.junit.jupiter.api.Test;

class GraphqlErrorRecorderTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.graphql-java-common-12.0";

  @Test
  void recordsGraphqlErrorUsingSelectedExceptionSignal() {
    InMemorySpanExporter spans = InMemorySpanExporter.create();
    InMemoryLogRecordExporter logs = InMemoryLogRecordExporter.create();
    try (OpenTelemetrySdk openTelemetry = createOpenTelemetry(spans, logs)) {
      GraphQLError error = mock(GraphQLError.class);
      when(error.getErrorType()).thenReturn(ErrorType.ValidationError);
      when(error.getMessage()).thenReturn("invalid field");

      String version = EmbeddedInstrumentationProperties.findVersion(INSTRUMENTATION_NAME);
      InstrumentationScopeInfo expectedScope =
          InstrumentationScopeInfo.builder(INSTRUMENTATION_NAME)
              .setVersion(version)
              .setSchemaUrl(SchemaUrls.V1_44_0)
              .build();
      Span span =
          openTelemetry
              .tracerBuilder(INSTRUMENTATION_NAME)
              .setInstrumentationVersion(version)
              .setSchemaUrl(SchemaUrls.V1_44_0)
              .build()
              .spanBuilder("GraphQL Operation")
              .setNoParent()
              .setSpanKind(SpanKind.INTERNAL)
              .startSpan();
      Context context = Context.root().with(span);

      // Completion callbacks may run with an unrelated current context.
      try (Scope ignored = Context.root().makeCurrent()) {
        new GraphqlErrorRecorder(openTelemetry, INSTRUMENTATION_NAME, SchemaUrls.V1_44_0)
            .record(context, error);
      }
      span.end();

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
                                      equalTo(EXCEPTION_TYPE, "ValidationError"),
                                      equalTo(EXCEPTION_MESSAGE, "invalid field")));
                } else {
                  assertThat(spanData.getEvents()).isEmpty();
                }
              });
      if (emitExceptionAsLogs()) {
        assertThat(logs.getFinishedLogRecordItems())
            .singleElement()
            .satisfies(
                log ->
                    assertThat(log)
                        .hasEventName("exception")
                        .hasSeverity(Severity.WARN)
                        .hasSpanContext(span.getSpanContext())
                        .hasInstrumentationScope(expectedScope)
                        .hasAttributesSatisfyingExactly(
                            equalTo(EXCEPTION_TYPE, "ValidationError"),
                            equalTo(EXCEPTION_MESSAGE, "invalid field")));
      } else {
        assertThat(logs.getFinishedLogRecordItems()).isEmpty();
      }
    }
  }

  private static OpenTelemetrySdk createOpenTelemetry(
      InMemorySpanExporter spans, InMemoryLogRecordExporter logs) {
    return OpenTelemetrySdk.builder()
        .setTracerProvider(
            SdkTracerProvider.builder().addSpanProcessor(SimpleSpanProcessor.create(spans)).build())
        .setLoggerProvider(
            SdkLoggerProvider.builder()
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(logs))
                .build())
        .build();
  }
}
