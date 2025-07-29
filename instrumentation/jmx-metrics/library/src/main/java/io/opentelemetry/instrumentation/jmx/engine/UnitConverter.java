/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is responsible for converting a value between metric units using defined conversion
 * algorithms.
 */
class UnitConverter {
  private static final Map<String, UnitConverter> conversionMappings = new HashMap<>();

  static {
    registerConversion("ms", "s", value -> value.doubleValue() / TimeUnit.SECONDS.toMillis(1));
    registerConversion("us", "s", value -> value.doubleValue() / TimeUnit.SECONDS.toMicros(1));
    registerConversion("ns", "s", value -> value.doubleValue() / TimeUnit.SECONDS.toNanos(1));
  }

  private final Function<Number, Number> convertingFunction;

  /**
   * Get an instance of converter that is able to convert a value from a given source to a target
   * unit.
   *
   * @param sourceUnit a source unit supported by requested converter
   * @param targetUnit a target unit supported by requested converter
   * @return an instance of converter, or {@literal null} if {@code sourceUnit} is {@literal null}
   *     or empty or if {@code targetUnit} is empty, which means that there is no conversion needed.
   * @throws IllegalArgumentException if matching converter was not found for provided units.
   */
  @Nullable
  public static UnitConverter getInstance(@Nullable String sourceUnit, String targetUnit) {
    if (sourceUnit == null || sourceUnit.isEmpty() || targetUnit.isEmpty()) {
      // No conversion is needed
      return null;
    }

    String converterKey = buildConverterKey(sourceUnit, targetUnit);
    UnitConverter converter = conversionMappings.get(converterKey);
    if (converter == null) {
      throw new IllegalArgumentException(
          "Unsupported conversion from [" + sourceUnit + "] to [" + targetUnit + "]");
    }

    return converter;
  }

  /**
   * Register new converter instance that can then be retrieved with {@link #getInstance(String,
   * String)}.
   *
   * @param sourceUnit a source unit supported by the converter
   * @param targetUnit a target unit supported by the converter
   * @param convertingFunction a function that implements algorithm of conversion between {@code
   *     sourceUnit} and {@code targetUnit}
   * @throws IllegalArgumentException if source or target unit is empty, or when there is converter
   *     already registered for given {@code sourceUnit} and {@code targetUnit}
   */
  // visible for testing
  static void registerConversion(
      String sourceUnit, String targetUnit, Function<Number, Number> convertingFunction) {
    if (sourceUnit.isEmpty()) {
      throw new IllegalArgumentException("Non empty sourceUnit must be provided");
    }
    if (targetUnit.isEmpty()) {
      throw new IllegalArgumentException("Non empty targetUnit must be provided");
    }

    String converterKey = buildConverterKey(sourceUnit, targetUnit);

    if (conversionMappings.containsKey(converterKey)) {
      throw new IllegalArgumentException(
          "Conversion from [" + sourceUnit + "] to [" + targetUnit + "] already defined");
    }
    conversionMappings.put(converterKey, new UnitConverter(convertingFunction));
  }

  private static String buildConverterKey(String sourceUnit, String targetUnit) {
    return sourceUnit + "->" + targetUnit;
  }

  /**
   * Create an instance of converter
   *
   * @param convertingFunction an algorithm applied when converting value
   */
  UnitConverter(Function<Number, Number> convertingFunction) {
    this.convertingFunction = convertingFunction;
  }

  public Number convert(Number value) {
    return convertingFunction.apply(value);
  }
}
