/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine.internal;

import java.util.function.Function;

/**
 * This class is responsible for converting a value using provided algorithm. This class is internal
 * and is hence not for public use. Its APIs are unstable and can change at any time.
 */
public class UnitConverter {
  private final Function<Number, Number> convertingFunction;
  private final boolean convertToDouble;

  /**
   * Create an instance of converter
   *
   * @param convertingFunction an algorithm applied when converting value
   * @param convertToDouble {@code true} indicates that conversion result is of type Double, {@code
   *     false} indicates that conversion result is of type Long
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
