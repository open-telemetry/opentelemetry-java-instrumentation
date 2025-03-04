/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine.unit;

import java.util.function.Function;

/** This class is responsible for converting a value using provided algorithm. */
public class UnitConverter {
  private final Function<Number, Number> convertingFunction;
  private final boolean convertToDouble;

  /**
   * Create an instance of converter
   *
   * @param convertingFunction an algorithm applied when converting value
   * @param convertToDouble indicates of algorithm will return floating point result. This must be
   *     in-sync with algorithm implementation.
   */
  UnitConverter(Function<Number, Number> convertingFunction, boolean convertToDouble) {
    this.convertingFunction = convertingFunction;
    this.convertToDouble = convertToDouble;
  }

  public Number convert(Number value) {
    return convertingFunction.apply(value);
  }

  public boolean isConvertingToDouble() {
    return convertToDouble;
  }
}
