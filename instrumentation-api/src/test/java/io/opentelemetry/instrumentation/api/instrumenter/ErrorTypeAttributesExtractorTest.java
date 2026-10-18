/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.ErrorAttributes.ERROR_TYPE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ErrorTypeAttributesExtractorTest {

  @RegisterExtension
  static final OpenTelemetryExtension otelTesting = OpenTelemetryExtension.create();

  private final AttributesExtractor<String, String> extractor = AttributesExtractor.errorType();

  @Test
  void extractsNothingOnStartOrSuccessfulEnd() {
    AttributesBuilder attributes = Attributes.builder();
    extractor.onStart(attributes, Context.root(), "request");
    assertThat(attributes.build()).isEmpty();

    extractor.onEnd(attributes, Context.root(), "request", "response", null);
    assertThat(attributes.build()).isEmpty();
  }

  @Test
  void extractsExceptionClassNameWithoutUnwrapping() {
    AttributesBuilder attributes = Attributes.builder();
    extractor.onEnd(attributes, Context.root(), "request", null, new TestException());
    assertThat(attributes.build())
        .isEqualTo(Attributes.of(ERROR_TYPE, TestException.class.getName()));

    extractor.onEnd(
        attributes, Context.root(), "request", null, new CompletionException(new TestException()));
    assertThat(attributes.build())
        .isEqualTo(Attributes.of(ERROR_TYPE, CompletionException.class.getName()));
  }

  @Test
  void nullErrorDoesNotOverwriteExistingClassification() {
    AttributesBuilder attributes = Attributes.builder().put(ERROR_TYPE, "specific");
    extractor.onEnd(attributes, Context.root(), "request", null, null);
    assertThat(attributes.build()).isEqualTo(Attributes.of(ERROR_TYPE, "specific"));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void normalizedErrorAndOverridesReachSpanAndListener(boolean override) {
    AtomicReference<Attributes> listenerAttributes = new AtomicReference<>();
    InstrumenterBuilder<String, String> builder =
        Instrumenter.<String, String>builder(
                otelTesting.getOpenTelemetry(), "test", request -> "operation")
            .addAttributesExtractor(extractor)
            .addOperationListener(recordEndAttributes(listenerAttributes));
    if (override) {
      builder.addAttributesExtractor(
          new AttributesExtractor<String, String>() {
            @Override
            public void onStart(
                AttributesBuilder attributes, Context parentContext, String request) {}

            @Override
            public void onEnd(
                AttributesBuilder attributes,
                Context context,
                String request,
                String response,
                Throwable error) {
              assertThat(error).isInstanceOf(TestException.class);
              attributes.put(ERROR_TYPE, "specific");
            }
          });
    }
    Instrumenter<String, String> instrumenter = builder.buildInstrumenter();
    Context context = instrumenter.start(Context.root(), "request");
    instrumenter.end(context, "request", null, new CompletionException(new TestException()));

    String expected = override ? "specific" : TestException.class.getName();
    assertThat(listenerAttributes.get().asMap())
        .isEqualTo(Attributes.of(ERROR_TYPE, expected).asMap());
    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasStatus(StatusData.error())
                            .hasAttributesSatisfyingExactly(equalTo(ERROR_TYPE, expected))));
  }

  @Test
  void respectsCustomErrorCauseExtractor() {
    Instrumenter<String, String> instrumenter =
        Instrumenter.<String, String>builder(
                otelTesting.getOpenTelemetry(), "test", request -> "operation")
            .setErrorCauseExtractor(error -> error.getCause())
            .addAttributesExtractor(extractor)
            .buildInstrumenter();
    Context context = instrumenter.start(Context.root(), "request");
    instrumenter.end(context, "request", null, new IllegalStateException(new TestException()));

    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace ->
                trace.hasSpansSatisfyingExactly(
                    span ->
                        span.hasAttributesSatisfyingExactly(
                            equalTo(ERROR_TYPE, TestException.class.getName()))));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void nullErrorIsNotClassifiedRegardlessOfSpanStatus(boolean errorStatus) {
    AtomicReference<Attributes> listenerAttributes = new AtomicReference<>();
    Instrumenter<String, String> instrumenter =
        Instrumenter.<String, String>builder(
                otelTesting.getOpenTelemetry(), "test", request -> "operation")
            .addAttributesExtractor(extractor)
            .addOperationListener(recordEndAttributes(listenerAttributes))
            .buildInstrumenter();
    Context context = instrumenter.start(Context.root(), "request");
    if (errorStatus) {
      Span.fromContext(context).setStatus(StatusCode.ERROR);
    }
    instrumenter.end(context, "request", "response", null);

    assertThat(listenerAttributes.get().asMap()).isEmpty();
    otelTesting
        .assertTraces()
        .hasTracesSatisfyingExactly(
            trace -> trace.hasSpansSatisfyingExactly(span -> span.hasTotalAttributeCount(0)));
  }

  private static OperationListener recordEndAttributes(AtomicReference<Attributes> attributes) {
    return new OperationListener() {
      @Override
      public Context onStart(Context context, Attributes startAttributes, long startNanos) {
        assertThat(startAttributes.asMap()).isEmpty();
        return context;
      }

      @Override
      public void onEnd(Context context, Attributes endAttributes, long endNanos) {
        attributes.set(endAttributes);
      }
    };
  }

  private static class TestException extends RuntimeException {}
}
