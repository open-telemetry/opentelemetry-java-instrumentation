/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import java.util.Locale;

public enum InstrumentationType {
  JAVAAGENT,
  LIBRARY;

  public static InstrumentationType fromString(String type) {
    return switch (type.toLowerCase(Locale.getDefault())) {
      case "javaagent" -> JAVAAGENT;
      case "library" -> LIBRARY;
      default -> throw new IllegalArgumentException("Unknown instrumentation type: " + type);
    };
  }
}
