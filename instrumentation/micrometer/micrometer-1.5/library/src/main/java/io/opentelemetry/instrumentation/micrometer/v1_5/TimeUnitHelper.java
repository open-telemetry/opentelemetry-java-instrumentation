/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TimeUnitHelper {

  private static final Logger logger = LoggerFactory.getLogger(OpenTelemetryMeterRegistry.class);

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
        logger.warn(
            "Invalid base time unit: '{}'; using microseconds as the base time unit instead",
            value);
        return TimeUnit.MILLISECONDS;
    }
  }

  static String getUnitString(TimeUnit unit) {
    switch (unit) {
      case NANOSECONDS:
        return "ns";
      case MICROSECONDS:
        return "us";
      case MILLISECONDS:
        return "ms";
      case SECONDS:
        return "s";
      case MINUTES:
        return "min";
      case HOURS:
        return "h";
      case DAYS:
        return "d";
    }
    throw new IllegalStateException("Should not ever happen");
  }

  private TimeUnitHelper() {}
}
