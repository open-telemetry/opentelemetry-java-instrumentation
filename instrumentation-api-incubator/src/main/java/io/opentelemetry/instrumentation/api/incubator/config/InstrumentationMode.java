/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;

/**
 * The instrumentation mode to use when configuring instrumentations via declarative configuration.
 */
public enum InstrumentationMode {
  /** Most instrumentations are enabled and configured with their default settings. */
  DEFAULT,
  /** All instrumentations are disabled by default. */
  NONE;

  public static InstrumentationMode from(String mode) {
    switch (mode) {
      case "none":
        return InstrumentationMode.NONE;
      case "default":
        return InstrumentationMode.DEFAULT;
      default:
        throw new IllegalArgumentException("Unknown instrumentation mode: " + mode);
    }
  }

  public static InstrumentationMode read(OpenTelemetry openTelemetry) {
    return from(
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "agent")
            .getString("instrumentation_mode", "default"));
  }
}
