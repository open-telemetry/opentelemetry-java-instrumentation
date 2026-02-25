/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MICROSECONDS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistry;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nullable;

final class TimeUnitParser {

  private static final Logger logger = Logger.getLogger(OpenTelemetryMeterRegistry.class.getName());

  static TimeUnit parseConfigValue(@Nullable String value) {
    if (value == null) {
      return SECONDS;
    }
    // short names are UCUM names
    // long names are just TimeUnit values lowercased
    switch (value.toLowerCase(Locale.ROOT)) {
      case "ns":
      case "nanoseconds":
        return NANOSECONDS;
      case "us":
      case "microseconds":
        return MICROSECONDS;
      case "ms":
      case "milliseconds":
        return MILLISECONDS;
      case "s":
      case "seconds":
        return SECONDS;
      case "min":
      case "minutes":
        return MINUTES;
      case "h":
      case "hours":
        return HOURS;
      case "d":
      case "days":
        return DAYS;
      default:
        if (logger.isLoggable(WARNING)) {
          logger.log(
              WARNING,
              "Invalid base time unit: \"{0}\"; using \"s\" as the base time unit instead",
              value);
        }
        return SECONDS;
    }
  }

  private TimeUnitParser() {}
}
