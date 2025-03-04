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

  private static final Map<String, Map<String, UnitConverter>> conversionMappings = new HashMap<>();

  static {
    registerConverter("ms", "s", value -> value.doubleValue() / TimeUnit.SECONDS.toMillis(1), true);
    registerConverter("ns", "s", value -> value.doubleValue() / TimeUnit.SECONDS.toNanos(1), true);
  }

  private UnitConverterFactory() {}

  public static UnitConverter getConverter(@Nullable String fromUnit, @Nullable String toUnit) {
    if (fromUnit == null || toUnit == null) {
      return null;
    }

    Map<String, UnitConverter> converters = conversionMappings.get(fromUnit);
    if (converters == null) {
      return null;
    }

    return converters.get(toUnit);
  }

  public static void registerConverter(
      String sourceUnit,
      String targetUnit,
      Function<Number, Number> convertingFunction,
      boolean convertToDouble) {
    Map<String, UnitConverter> converters =
        conversionMappings.computeIfAbsent(sourceUnit, k -> new HashMap<>());

    if (converters.containsKey(targetUnit)) {
      throw new IllegalArgumentException(
          "Converter from " + sourceUnit + " to " + targetUnit + " already registered");
    }
    converters.put(targetUnit, new UnitConverter(convertingFunction, convertToDouble));
  }
}
