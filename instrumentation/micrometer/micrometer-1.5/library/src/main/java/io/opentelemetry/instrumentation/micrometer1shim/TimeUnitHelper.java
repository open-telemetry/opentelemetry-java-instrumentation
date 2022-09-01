/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer1shim;

import java.util.concurrent.TimeUnit;

final class TimeUnitHelper {

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
