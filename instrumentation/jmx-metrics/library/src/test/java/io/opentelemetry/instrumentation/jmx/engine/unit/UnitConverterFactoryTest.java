/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine.unit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UnitConverterFactoryTest {

  @ParameterizedTest
  @CsvSource({
    "1000000000,ns, 1.0",
    "25,ns, 0.000000025",
    "96614101945,ns, 96.614101945",
    "0,ns, 0",
    "1000000,us, 1.0",
    "25,us, 0.000025",
    "96614101945,us, 96614.101945",
    "0,ns, 0",
    "1000,ms, 1.0",
    "25,ms, 0.025",
    "9661410,ms, 9661.41",
    "0,ms, 0",
  })
  void shouldSupportPredefined_to_s_Converters(
      Long originalValue, String originalUnit, Double expectedConvertedValue) {
    // Given
    String targetUnit = "s";

    // When
    UnitConverter converter = UnitConverterFactory.getConverter(originalUnit, targetUnit);
    Number actualValue = converter.convert(originalValue);

    // Then
    assertEquals(expectedConvertedValue, actualValue);
    assertTrue(converter.isConvertingToDouble());
  }

  @Test
  void shouldRegisterConverter() {
    // Given
    String sourceUnit = "MB";
    String targetUnit = "By";

    // When
    UnitConverterFactory.registerConverter(
        sourceUnit, targetUnit, (megaBytes) -> megaBytes.doubleValue() * 1024 * 1024, false);
    UnitConverter converter = UnitConverterFactory.getConverter(sourceUnit, targetUnit);
    Number actualValue = converter.convert(1.5);

    // Then
    assertEquals(1572864, actualValue.longValue());
    assertFalse(converter.isConvertingToDouble());
  }

  @ParameterizedTest
  @CsvSource({
    "--, --",
    "ms, non-existing",
    "non-existing, s",
  })
  void shouldHandleNonExistingConverter(String sourceUnit, String targetUnit) {
    assertThatThrownBy(() -> UnitConverterFactory.getConverter(sourceUnit, targetUnit))
        .hasMessage("No [" + sourceUnit + "] to [" + targetUnit + "] unit converter");
  }

  @ParameterizedTest
  @CsvSource({
    ", s", // null -> "s"
    "'', s", // "" -> "s"
  })
  void shouldSkipConversionWhenSourceUnitNotSpecified(String sourceUnit, String targetUnit) {
    UnitConverter converter = UnitConverterFactory.getConverter(sourceUnit, targetUnit);
    assertNull(converter);
  }

  @ParameterizedTest
  @CsvSource({
    "'', By", "By, ''",
  })
  void shouldNotAllowRegisteringConverterWithAnyUnitEmpty(String sourceUnit, String targetUnit) {
    assertThatThrownBy(
            () ->
                UnitConverterFactory.registerConverter(sourceUnit, targetUnit, (value) -> 0, false))
        .hasMessageMatching("Non empty .+Unit must be provided");
  }

  @Test
  void shouldNotAllowRegisteringAgainAlreadyExistingConverter() {
    assertThatThrownBy(() -> UnitConverterFactory.registerConverter("ms", "s", (v) -> 0, false))
        .hasMessage("Converter from [ms] to [s] already registered");
  }
}
