/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.ContextCustomizer;
import io.opentelemetry.instrumentation.api.instrumenter.OperationMetrics;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumentationCustomizerTest {

  @Mock private OperationMetrics operationMetrics;
  @Mock private AttributesExtractor<Object, Object> attributesExtractor;
  @Mock private ContextCustomizer<Object> contextCustomizer;
  @Mock private SpanNameExtractor<Object> spanNameExtractor;

  private InstrumentationCustomizer customizer;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    customizer =
        new InstrumentationCustomizer() {

          @Override
          public Predicate<String> instrumentationNamePredicate() {
            return name -> name.startsWith("test.instrumentation");
          }

          @Override
          public OperationMetrics getOperationMetrics() {
            return operationMetrics;
          }

          @Override
          public <REQUEST, RESPONSE>
              AttributesExtractor<REQUEST, RESPONSE> getAttributesExtractor() {
            return (AttributesExtractor<REQUEST, RESPONSE>) attributesExtractor;
          }

          @Override
          public <REQUEST> ContextCustomizer<REQUEST> getContextCustomizer() {
            return (ContextCustomizer<REQUEST>) contextCustomizer;
          }

          @Override
          public <REQUEST> SpanNameExtractor<REQUEST> getSpanNameExtractor() {
            return (SpanNameExtractor<REQUEST>) spanNameExtractor;
          }
        };
  }

  @Test
  void testInstrumentationNamePredicate() {
    assertThat(customizer.instrumentationNamePredicate().test("test.instrumentation.example"))
        .isTrue();
    assertThat(customizer.instrumentationNamePredicate().test("other.instrumentation.example"))
        .isFalse();
  }

  @Test
  void testGetOperationMetrics() {
    OperationMetrics metrics = customizer.getOperationMetrics();
    assertThat(metrics).isSameAs(operationMetrics);
  }

  @Test
  void testGetAttributesExtractor() {
    AttributesExtractor<Object, Object> extractor = customizer.getAttributesExtractor();
    assertThat(extractor).isSameAs(attributesExtractor);
  }

  @Test
  void testGetContextCustomizer() {
    assertThat(customizer.getContextCustomizer()).isSameAs(contextCustomizer);
  }

  @Test
  void testGetSpanNameExtractor() {
    SpanNameExtractor<Object> extractor = customizer.getSpanNameExtractor();
    assertThat(extractor).isSameAs(spanNameExtractor);
  }
}
