/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.api.incubator.config.internal.ExtendedDeclarativeConfigProperties;
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
      RuntimeMetricsBuilder builder, OpenTelemetry openTelemetry, String instrumentationMode) {
    ExtendedDeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry");
    if (!config.getBoolean("enabled", instrumentationMode.equals("default"))) {
      // nothing is enabled
      return null;
    }

    if (config.getBoolean("emit_experimental_telemetry/development", false)) {
      builder.emitExperimentalTelemetry();
    }

    if (config.getBoolean("capture_gc_cause", false)) {
      builder.captureGcCause();
    }

    return builder.build();
  }
}
