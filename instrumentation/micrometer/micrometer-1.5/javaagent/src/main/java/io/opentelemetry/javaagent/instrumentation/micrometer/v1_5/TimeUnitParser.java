/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static java.util.logging.Level.WARNING;

import io.opentelemetry.micrometer1shim.OpenTelemetryMeterRegistry;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class TimeUnitParser {

  private static final Logger logger = Logger.getLogger(OpenTelemetryMeterRegistry.class.getName());

  static TimeUnit parseConfigValue(@Nullable String value) {
    if (value == null) {
      return TimeUnit.MILLISECONDS;
    }
    // short names are UCUM names
    // long names are just TimeUnit values lowercased
    switch (value.toLowerCase(Locale.ROOT)) {
      case "ns":
      case "nanoseconds":
        return TimeUnit.NANOSECONDS;
      case "us":
      case "microseconds":
        return TimeUnit.MICROSECONDS;
      case "ms":
      case "milliseconds":
        return TimeUnit.MILLISECONDS;
      case "s":
      case "seconds":
        return TimeUnit.SECONDS;
      case "min":
      case "minutes":
        return TimeUnit.MINUTES;
      case "h":
      case "hours":
        return TimeUnit.HOURS;
      case "d":
      case "days":
        return TimeUnit.DAYS;
      default:
        if (logger.isLoggable(WARNING)) {
          logger.log(
              WARNING,
              "Invalid base time unit: '{0}'; using 'ms' as the base time unit instead",
              value);
        }
        return TimeUnit.MILLISECONDS;
    }
  }

  private TimeUnitParser() {}
}
