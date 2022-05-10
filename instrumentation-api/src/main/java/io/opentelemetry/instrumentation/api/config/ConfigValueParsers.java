/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import static java.util.Collections.unmodifiableList;
import static java.util.Collections.unmodifiableMap;

import java.time.Duration;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

// most of the parsing code copied from
// https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/src/main/java/io/opentelemetry/sdk/autoconfigure/DefaultConfigProperties.java
@SuppressWarnings("UnusedException")
final class ConfigValueParsers {

  static boolean parseBoolean(@SuppressWarnings("unused") String propertyName, String value) {
    return Boolean.parseBoolean(value);
  }

  static int parseInt(String propertyName, String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw newInvalidPropertyException(propertyName, value, "integer");
    }
  }

  static long parseLong(String propertyName, String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw newInvalidPropertyException(propertyName, value, "long");
    }
  }

  static double parseDouble(String propertyName, String value) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      throw newInvalidPropertyException(propertyName, value, "double");
    }
  }

  private static ConfigParsingException newInvalidPropertyException(
      String name, String value, String type) {
    throw new ConfigParsingException(
        "Invalid value for property " + name + "=" + value + ". Must be a " + type + ".");
  }

  static List<String> parseList(@SuppressWarnings("unused") String propertyName, String value) {
    return unmodifiableList(filterBlanks(value.split(",")));
  }

  static Map<String, String> parseMap(String propertyName, String value) {
    return unmodifiableMap(
        parseList(propertyName, value).stream()
            .map(keyValuePair -> trim(keyValuePair.split("=", 2)))
            .map(
                splitKeyValuePairs -> {
                  if (splitKeyValuePairs.size() != 2 || splitKeyValuePairs.get(0).isEmpty()) {
                    throw new ConfigParsingException(
                        "Invalid map property: " + propertyName + "=" + value);
                  }
                  return new AbstractMap.SimpleImmutableEntry<>(
                      splitKeyValuePairs.get(0), splitKeyValuePairs.get(1));
                })
            // If duplicate keys, prioritize later ones similar to duplicate system properties on a
            // Java command line.
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    Map.Entry::getValue,
                    (first, next) -> next,
                    LinkedHashMap::new)));
  }

  private static List<String> filterBlanks(String[] values) {
    return Arrays.stream(values)
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .collect(Collectors.toList());
  }

  private static List<String> trim(String[] values) {
    return Arrays.stream(values).map(String::trim).collect(Collectors.toList());
  }

  static Duration parseDuration(String propertyName, String value) {
    String unitString = getUnitString(value);
    String numberString = value.substring(0, value.length() - unitString.length());
    try {
      long rawNumber = Long.parseLong(numberString.trim());
      TimeUnit unit = getDurationUnit(unitString.trim());
      return Duration.ofMillis(TimeUnit.MILLISECONDS.convert(rawNumber, unit));
    } catch (NumberFormatException e) {
      throw new ConfigParsingException(
          "Invalid duration property "
              + propertyName
              + "="
              + value
              + ". Expected number, found: "
              + numberString);
    } catch (ConfigParsingException ex) {
      throw new ConfigParsingException(
          "Invalid duration property " + propertyName + "=" + value + ". " + ex.getMessage());
    }
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
        throw new ConfigParsingException("Invalid duration string, found: " + unitString);
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
