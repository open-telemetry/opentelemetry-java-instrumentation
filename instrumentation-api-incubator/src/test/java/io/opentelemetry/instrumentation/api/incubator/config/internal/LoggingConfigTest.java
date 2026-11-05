/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class LoggingConfigTest {

  @Test
  void commonSelectorFiltersStructuredAttributes() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    DeclarativeConfigProperties selectorConfig =
        openTelemetry
            .getInstrumentationConfig("common")
            .get("logging")
            .get("structured_attributes");
    when(selectorConfig.getScalarList("included", String.class)).thenReturn(singletonList("key*"));
    when(selectorConfig.getScalarList("excluded", String.class)).thenReturn(singletonList("key2"));

    Predicate<String> selector = LoggingConfig.resolveStructuredAttributes(openTelemetry);

    assertThat(selector.test("key1")).isTrue();
    assertThat(selector.test("key2")).isFalse();
    assertThat(selector.test("other")).isFalse();
  }

  @Test
  void absentSelectorCapturesEverything() {
    Predicate<String> selector = LoggingConfig.resolveStructuredAttributes(mockOpenTelemetry());

    assertThat(selector.test("anything")).isTrue();
  }

  @Test
  void emptySelectorCapturesEverything() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    DeclarativeConfigProperties selectorConfig =
        openTelemetry
            .getInstrumentationConfig("common")
            .get("logging")
            .get("structured_attributes");
    when(selectorConfig.getScalarList("included", String.class)).thenReturn(emptyList());
    when(selectorConfig.getScalarList("excluded", String.class)).thenReturn(emptyList());

    Predicate<String> selector = LoggingConfig.resolveStructuredAttributes(openTelemetry);

    assertThat(selector.test("anything")).isTrue();
  }

  @Test
  void excludeAllSelectorRemainsEffective() {
    ExtendedOpenTelemetry openTelemetry = mockOpenTelemetry();
    DeclarativeConfigProperties selectorConfig =
        openTelemetry
            .getInstrumentationConfig("common")
            .get("logging")
            .get("structured_attributes");
    when(selectorConfig.getScalarList("included", String.class)).thenReturn(emptyList());
    when(selectorConfig.getScalarList("excluded", String.class)).thenReturn(singletonList("*"));

    Predicate<String> selector = LoggingConfig.resolveStructuredAttributes(openTelemetry);

    assertThat(selector.test("anything")).isFalse();
  }

  private static ExtendedOpenTelemetry mockOpenTelemetry() {
    return mock(ExtendedOpenTelemetry.class, RETURNS_DEEP_STUBS);
  }
}
