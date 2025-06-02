/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.instrumenter;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumentationCustomizerTest {

  @Mock private AttributesExtractor<Object, Object> attributesExtractor;
  @Mock private OperationMetrics operationMetrics;

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
          public <REQUEST, RESPONSE>
              AttributesExtractor<REQUEST, RESPONSE> getAttributesExtractor() {
            return (AttributesExtractor<REQUEST, RESPONSE>) attributesExtractor;
          }

          @Override
          public OperationMetrics getOperationMetrics() {
            return operationMetrics;
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
  void testGetAttributesExtractor() {
    AttributesExtractor<Object, Object> extractor = customizer.getAttributesExtractor();
    assertThat(extractor).isSameAs(attributesExtractor);
  }

  @Test
  void testGetOperationMetrics() {
    OperationMetrics metrics = customizer.getOperationMetrics();
    assertThat(metrics).isSameAs(operationMetrics);
  }
}
