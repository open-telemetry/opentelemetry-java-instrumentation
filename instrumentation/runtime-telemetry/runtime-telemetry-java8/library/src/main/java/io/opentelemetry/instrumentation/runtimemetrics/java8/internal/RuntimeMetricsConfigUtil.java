/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.runtimemetrics.java8.RuntimeMetricsBuilder;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RuntimeMetricsConfigUtil {

  @CanIgnoreReturnValue
  public static RuntimeMetricsBuilder configure(
      RuntimeMetricsBuilder builder, OpenTelemetry openTelemetry) {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry");

    if (config.getBoolean("emit_experimental_telemetry/development", false)) {
      builder.emitExperimentalTelemetry();
    }

    if (config.getBoolean("capture_gc_cause", false)) {
      builder.captureGcCause();
    }

    return builder;
  }

  private RuntimeMetricsConfigUtil() {}
}
