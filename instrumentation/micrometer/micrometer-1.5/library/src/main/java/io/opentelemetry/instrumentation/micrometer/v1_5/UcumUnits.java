/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import java.util.HashMap;
import java.util.Map;

final class UcumUnits {

  // Micrometer base units are free-form English words, while OpenTelemetry uses UCUM.
  // Curly-brace entries are UCUM annotations, which semantic conventions require to be
  // grammatically singular. Only the unit string is rewritten, never the value, so "percent" maps
  // to "%" and not to "1" (a 0..1 fraction). Units not in this table are passed through unchanged.
  private static final Map<String, String> UNITS = new HashMap<>();

  static {
    UNITS.put("bytes", "By");
    UNITS.put("seconds", "s");
    UNITS.put("percent", "%");
    UNITS.put("buffers", "{buffer}");
    UNITS.put("classes", "{class}");
    UNITS.put("connections", "{connection}");
    UNITS.put("events", "{event}");
    UNITS.put("files", "{file}");
    UNITS.put("messages", "{message}");
    UNITS.put("objects", "{object}");
    UNITS.put("operations", "{operation}");
    UNITS.put("records", "{record}");
    UNITS.put("requests", "{request}");
    UNITS.put("rows", "{row}");
    UNITS.put("sessions", "{session}");
    UNITS.put("tasks", "{task}");
    UNITS.put("threads", "{thread}");
  }

  static String normalize(String baseUnit) {
    String normalized = UNITS.get(baseUnit);
    return normalized != null ? normalized : baseUnit;
  }

  private UcumUnits() {}
}
