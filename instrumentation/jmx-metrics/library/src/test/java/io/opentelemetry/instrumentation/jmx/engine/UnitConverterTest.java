/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class UnitConverterTest {

  @ParameterizedTest
  @MethodSource("predefinedToSecondsConversions")
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

  // Arguments: originalValue, originalUnit, expectedConvertedValue
  static Stream<Arguments> predefinedToSecondsConversions() {
    return Stream.of(
        Arguments.of(1000000000L, "ns", 1.0),
        Arguments.of(25L, "ns", 0.000000025),
        Arguments.of(96614101945L, "ns", 96.614101945),
        Arguments.of(0L, "ns", 0.0),
        Arguments.of(1000000L, "us", 1.0),
        Arguments.of(25L, "us", 0.000025),
        Arguments.of(96614101945L, "us", 96614.101945),
        Arguments.of(0L, "ns", 0.0),
        Arguments.of(1000L, "ms", 1.0),
        Arguments.of(25L, "ms", 0.025),
        Arguments.of(9661410L, "ms", 9661.41),
        Arguments.of(0L, "ms", 0.0));
  }

  @ParameterizedTest
  @MethodSource("unsupportedConversions")
  void shouldHandleUnsupportedConversion(String sourceUnit, String targetUnit) {
    assertThatThrownBy(() -> UnitConverter.getInstance(sourceUnit, targetUnit))
        .hasMessage("Unsupported conversion from [" + sourceUnit + "] to [" + targetUnit + "]");
  }

  // Arguments: sourceUnit, targetUnit
  static Stream<Arguments> unsupportedConversions() {
    return Stream.of(
        Arguments.of("--", "--"),
        Arguments.of("ms", "non-existing"),
        Arguments.of("non-existing", "s"));
  }

  @ParameterizedTest
  @MethodSource("sourceUnitNotSpecified")
  void shouldSkipConversionWhenSourceUnitNotSpecified(String sourceUnit, String targetUnit) {
    UnitConverter converter = UnitConverter.getInstance(sourceUnit, targetUnit);
    assertThat(converter).isNull();
  }

  // Arguments: sourceUnit, targetUnit
  static Stream<Arguments> sourceUnitNotSpecified() {
    return Stream.of(
        Arguments.of(null, "s"), // null -> "s"
        Arguments.of("", "s"), // "" -> "s"
        Arguments.of("1", "")); // empty target unit
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
  @MethodSource("emptyUnits")
  void shouldNotAllowRegisteringConversionWithAnyUnitEmpty(String sourceUnit, String targetUnit) {
    assertThatThrownBy(() -> UnitConverter.registerConversion(sourceUnit, targetUnit, (value) -> 0))
        .hasMessageMatching("Non empty .+Unit must be provided");
  }

  // Arguments: sourceUnit, targetUnit
  static Stream<Arguments> emptyUnits() {
    return Stream.of(Arguments.of("", "By"), Arguments.of("By", ""));
  }

  @Test
  void shouldNotAllowRegisteringAgainAlreadyExistingConversion() {
    assertThatThrownBy(() -> UnitConverter.registerConversion("ms", "s", (v) -> 0))
        .hasMessage("Conversion from [ms] to [s] already defined");
  }
}
