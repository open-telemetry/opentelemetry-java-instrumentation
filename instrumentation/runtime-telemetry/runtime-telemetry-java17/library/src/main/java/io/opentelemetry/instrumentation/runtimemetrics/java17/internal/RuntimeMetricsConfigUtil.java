/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetricsBuilder;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RuntimeMetricsConfigUtil {

  /**
   * By default, don't use any JFR metrics. May change this once semantic conventions are updated.
   * If enabled, default to only the metrics not already covered by runtime-telemetry-java8
   */
  public static boolean allEnabled(OpenTelemetry openTelemetry) {
    return DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry_java17")
        .getBoolean("enable_all", false);
  }

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
