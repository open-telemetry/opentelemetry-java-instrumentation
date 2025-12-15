/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Duration parsing copied from
 * https://github.com/open-telemetry/opentelemetry-java/blob/b9f98a87941dcbd578ef518685d5fe699f2f8c8f/sdk-extensions/autoconfigure-spi/src/main/java/io/opentelemetry/sdk/autoconfigure/spi/internal/DefaultConfigProperties.java
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
final class DurationParser {

  static Duration parseDuration(String value) {
    String unitString = getUnitString(value);
    // TODO: Environment variables have unknown encoding.  `trim()` may cut codepoints oddly
    // but likely we'll fail for malformed unit string either way.
    String numberString = value.substring(0, value.length() - unitString.length());
    long rawNumber = Long.parseLong(numberString.trim());
    TimeUnit unit = getDurationUnit(unitString.trim());
    return Duration.ofNanos(TimeUnit.NANOSECONDS.convert(rawNumber, unit));
  }

  /** Returns the TimeUnit associated with a unit string. Defaults to milliseconds. */
  private static TimeUnit getDurationUnit(String unitString) {
    switch (unitString) {
      case "us":
        return TimeUnit.MICROSECONDS;
      case "ns":
        return TimeUnit.NANOSECONDS;
      case "": // Fallthrough expected
      case "ms":
        return TimeUnit.MILLISECONDS;
      case "s":
        return TimeUnit.SECONDS;
      case "m":
        return TimeUnit.MINUTES;
      case "h":
        return TimeUnit.HOURS;
      case "d":
        return TimeUnit.DAYS;
      default:
        throw new IllegalArgumentException("Invalid duration string, found: " + unitString);
    }
  }

  /**
   * Fragments the 'units' portion of a config value from the 'value' portion.
   *
   * <p>E.g. "1ms" would return the string "ms".
   */
  private static String getUnitString(String rawValue) {
    int lastDigitIndex = rawValue.length() - 1;
    while (lastDigitIndex >= 0) {
      char c = rawValue.charAt(lastDigitIndex);
      if (Character.isDigit(c)) {
        break;
      }
      lastDigitIndex -= 1;
    }
    // Pull everything after the last digit.
    return rawValue.substring(lastDigitIndex + 1);
  }

  private DurationParser() {}
}
