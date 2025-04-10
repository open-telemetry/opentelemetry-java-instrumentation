/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import java.util.Locale;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum InstrumentationClassification {
  LIBRARY,
  CUSTOM,
  INTERNAL;

  @Nullable
  public static InstrumentationClassification fromString(@Nullable String type) {
    if (type == null) {
      return null;
    }
    return switch (type.toLowerCase(Locale.getDefault())) {
      case "library" -> LIBRARY;
      case "internal" -> INTERNAL;
      case "custom" -> CUSTOM;
      default -> null;
    };
  }

  @Override
  public String toString() {
    return name().toLowerCase(Locale.getDefault());
  }
}
