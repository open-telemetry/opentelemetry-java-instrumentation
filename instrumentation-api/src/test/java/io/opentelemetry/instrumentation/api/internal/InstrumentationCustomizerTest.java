/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.mockito.Mockito.verify;

import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.Arrays;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumentationCustomizerTest {

  private static final String TEST_INSTRUMENTATION_NAME = "test.instrumentation";

  @Mock private InstrumenterBuilder<Object, Object> builder;
  @Mock private OperationMetrics operationMetrics;
  @Mock private AttributesExtractor<Object, Object> attributesExtractor;
  @Mock private AttributesExtractor<Object, Object> secondAttributesExtractor;
  @Mock private ContextCustomizer<Object> contextCustomizer;
  @Mock private SpanNameExtractor<Object> spanNameExtractor;

  private InstrumenterCustomizer customizer;

  @BeforeEach
  void setUp() {
    builder.spanNameExtractor = spanNameExtractor;
    customizer =
        new InstrumenterCustomizerImpl(builder) {
          @Override
          public String getInstrumentationName() {
            return TEST_INSTRUMENTATION_NAME;
          }
        };
  }

  @Test
  void testGetInstrumentationName() {
    assertThat(customizer.getInstrumentationName()).isEqualTo(TEST_INSTRUMENTATION_NAME);
  }

  @Test
  void testAddAttributesExtractor() {
    InstrumenterCustomizer result = customizer.addAttributesExtractor(attributesExtractor);

    verify(builder).addAttributesExtractor(attributesExtractor);
    assertThat(result).isSameAs(customizer);
  }

  @Test
  void testAddAttributesExtractors() {
    Iterable<AttributesExtractor<Object, Object>> extractors =
        Arrays.asList(attributesExtractor, secondAttributesExtractor);

    InstrumenterCustomizer result = customizer.addAttributesExtractors(extractors);

    verify(builder).addAttributesExtractors(extractors);
    assertThat(result).isSameAs(customizer);
  }

  @Test
  void testAddOperationMetrics() {
    InstrumenterCustomizer result = customizer.addOperationMetrics(operationMetrics);

    verify(builder).addOperationMetrics(operationMetrics);
    assertThat(result).isSameAs(customizer);
  }

  @Test
  void testAddContextCustomizer() {
    InstrumenterCustomizer result = customizer.addContextCustomizer(contextCustomizer);

    verify(builder).addContextCustomizer(contextCustomizer);
    assertThat(result).isSameAs(customizer);
  }

  @Test
  @SuppressWarnings("unchecked")
  void testSetSpanNameExtractor() {
    Function<SpanNameExtractor<?>, SpanNameExtractor<?>> transformer =
        original ->
            (SpanNameExtractor<Object>)
                request -> "custom:" + ((SpanNameExtractor<Object>) original).extract(request);

    SpanNameExtractor<Object> originalExtractor = request -> "original";
    builder.spanNameExtractor = originalExtractor;

    customizer.setSpanNameExtractor(transformer);

    SpanNameExtractor<Object> updatedExtractor = builder.spanNameExtractor;
    String result = updatedExtractor.extract(new Object());
    assertThat(result).isEqualTo("custom:original");
  }
}
