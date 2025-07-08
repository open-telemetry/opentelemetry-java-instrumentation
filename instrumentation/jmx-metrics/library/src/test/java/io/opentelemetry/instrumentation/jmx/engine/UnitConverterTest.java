/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UnitConverterTest {

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
  void shouldSupportPredefined_to_s_Conversions(
      Long originalValue, String originalUnit, Double expectedConvertedValue) {
    // Given
    String targetUnit = "s";

    // When
    UnitConverter converter = UnitConverter.getInstance(originalUnit, targetUnit);
    Number actualValue = converter.convert(originalValue);

    // Then
    assertEquals(expectedConvertedValue, actualValue);
  }

  @ParameterizedTest
  @CsvSource({
    "--, --",
    "ms, non-existing",
    "non-existing, s",
  })
  void shouldHandleUnsupportedConversion(String sourceUnit, String targetUnit) {
    assertThatThrownBy(() -> UnitConverter.getInstance(sourceUnit, targetUnit))
        .hasMessage("Unsupported conversion from [" + sourceUnit + "] to [" + targetUnit + "]");
  }

  @ParameterizedTest
  @CsvSource({
    ", s", // null -> "s"
    "'', s", // "" -> "s"
    "1, ''", // empty target unit
  })
  void shouldSkipConversionWhenSourceUnitNotSpecified(String sourceUnit, String targetUnit) {
    UnitConverter converter = UnitConverter.getInstance(sourceUnit, targetUnit);
    assertThat(converter).isNull();
  }

  @Test
  void shouldRegisterNewConversion() {
    // Given
    String sourceUnit = "h";
    String targetUnit = "s";

    // When
    UnitConverter.registerConversion("h", "s", hours -> hours.doubleValue() * 3600.0);
    UnitConverter converter = UnitConverter.getInstance(sourceUnit, targetUnit);
    Number actualValue = converter.convert(1.5);

    // Then
    assertEquals(5400.0, actualValue);
  }

  @ParameterizedTest
  @CsvSource({
    "'', By", "By, ''",
  })
  void shouldNotAllowRegisteringConversionWithAnyUnitEmpty(String sourceUnit, String targetUnit) {
    assertThatThrownBy(() -> UnitConverter.registerConversion(sourceUnit, targetUnit, (value) -> 0))
        .hasMessageMatching("Non empty .+Unit must be provided");
  }

  @Test
  void shouldNotAllowRegisteringAgainAlreadyExistingConversion() {
    assertThatThrownBy(() -> UnitConverter.registerConversion("ms", "s", (v) -> 0))
        .hasMessage("Conversion from [ms] to [s] already defined");
  }
}
