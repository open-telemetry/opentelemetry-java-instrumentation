/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import java.util.Locale;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
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

  @Override
  public String toString() {
    return name().toLowerCase(Locale.getDefault());
  }
}
