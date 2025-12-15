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
        DeclarativeConfigUtil.getBoolean(openTelemetry, "java", "common", "default_enabled")
            .orElse(true);
    if (!DeclarativeConfigUtil.getBoolean(openTelemetry, "java", "runtime_telemetry", "enabled")
        .orElse(defaultEnabled)) {
      // nothing is enabled
      return null;
    }

    if (DeclarativeConfigUtil.getBoolean(
            openTelemetry, "java", "runtime_telemetry", "emit_experimental_telemetry")
        .orElse(false)) {
      builder.emitExperimentalTelemetry();
    }

    if (DeclarativeConfigUtil.getBoolean(
            openTelemetry, "java", "runtime_telemetry", "capture_gc_cause")
        .orElse(false)) {
      builder.captureGcCause();
    }

    return builder.build();
  }
}
