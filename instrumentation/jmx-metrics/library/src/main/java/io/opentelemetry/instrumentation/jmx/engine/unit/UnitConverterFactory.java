/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine.unit;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.annotation.Nullable;

public class UnitConverterFactory {
  private static final Map<String, UnitConverter> conversionMappings = new HashMap<>();

  static {
    registerConverter("ms", "s", value -> value.doubleValue() / TimeUnit.SECONDS.toMillis(1), true);
    registerConverter("us", "s", value -> value.doubleValue() / TimeUnit.SECONDS.toMicros(1), true);
    registerConverter("ns", "s", value -> value.doubleValue() / TimeUnit.SECONDS.toNanos(1), true);
  }

  private UnitConverterFactory() {}

  @Nullable
  public static UnitConverter getConverter(@Nullable String sourceUnit, String targetUnit) {
    if (targetUnit.isEmpty()) {
      throw new IllegalArgumentException("Non empty targetUnit must be provided");
    }

    if (sourceUnit == null || sourceUnit.isEmpty()) {
      // No conversion is needed
      return null;
    }

    String converterKey = getConverterKey(sourceUnit, targetUnit);
    UnitConverter converter = conversionMappings.get(converterKey);
    if (converter == null) {
      throw new IllegalArgumentException(
          "No [" + sourceUnit + "] to [" + targetUnit + "] unit converter");
    }

    return converter;
  }

  // visible for testing
  static void registerConverter(
      String sourceUnit,
      String targetUnit,
      Function<Number, Number> convertingFunction,
      boolean convertToDouble) {
    if (sourceUnit.isEmpty()) {
      throw new IllegalArgumentException("Non empty sourceUnit must be provided");
    }
    if (targetUnit.isEmpty()) {
      throw new IllegalArgumentException("Non empty targetUnit must be provided");
    }

    String converterKey = getConverterKey(sourceUnit, targetUnit);

    if (conversionMappings.containsKey(converterKey)) {
      throw new IllegalArgumentException(
          "Converter from [" + sourceUnit + "] to [" + targetUnit + "] already registered");
    }
    conversionMappings.put(converterKey, new UnitConverter(convertingFunction, convertToDouble));
  }

  private static String getConverterKey(String sourceUnit, String targetUnit) {
    return sourceUnit + "->" + targetUnit;
  }
}
