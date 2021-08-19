/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class ConfigValueParsers {

  static List<String> parseList(String value) {
    String[] tokens = value.split(",", -1);
    // Remove whitespace from each item.
    for (int i = 0; i < tokens.length; i++) {
      tokens[i] = tokens[i].trim();
    }
    return Collections.unmodifiableList(Arrays.asList(tokens));
  }

  static Map<String, String> parseMap(String value) {
    Map<String, String> result = new LinkedHashMap<>();
    for (String token : value.split(",", -1)) {
      token = token.trim();
      String[] parts = token.split("=", -1);
      if (parts.length != 2) {
        throw new IllegalArgumentException(
            "Invalid map config part, should be formatted key1=value1,key2=value2: " + value);
      }
      result.put(parts[0], parts[1]);
    }
    return Collections.unmodifiableMap(result);
  }

  static Duration parseDuration(String value) {
    String unitString = getUnitString(value);
    String numberString = value.substring(0, value.length() - unitString.length());
    long rawNumber = Long.parseLong(numberString.trim());
    TimeUnit unit = getDurationUnit(unitString.trim());
    return Duration.ofMillis(TimeUnit.MILLISECONDS.convert(rawNumber, unit));
  }

  /** Returns the TimeUnit associated with a unit string. Defaults to milliseconds. */
  private static TimeUnit getDurationUnit(String unitString) {
    switch (unitString) {
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

  private ConfigValueParsers() {}
}
