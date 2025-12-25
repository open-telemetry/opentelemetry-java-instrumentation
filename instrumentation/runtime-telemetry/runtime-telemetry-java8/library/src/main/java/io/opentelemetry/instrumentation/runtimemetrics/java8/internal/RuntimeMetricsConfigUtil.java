/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.runtimemetrics.java8.RuntimeMetrics;
import io.opentelemetry.instrumentation.runtimemetrics.java8.RuntimeMetricsBuilder;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RuntimeMetricsConfigUtil {
  private RuntimeMetricsConfigUtil() {}

  @Nullable
  public static RuntimeMetrics configure(
      RuntimeMetricsBuilder builder, OpenTelemetry openTelemetry) {
    boolean defaultEnabled =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "common")
            .getBoolean("default_enabled", true);
    if (!DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry")
        .getBoolean("enabled", defaultEnabled)) {
      // nothing is enabled
      return null;
    }

    if (DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry")
        .getBoolean("emit_experimental_telemetry/development", false)) {
      builder.emitExperimentalTelemetry();
    }

    if (DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry")
        .getBoolean("capture_gc_cause", false)) {
      builder.captureGcCause();
    }

    return builder.build();
  }
}
