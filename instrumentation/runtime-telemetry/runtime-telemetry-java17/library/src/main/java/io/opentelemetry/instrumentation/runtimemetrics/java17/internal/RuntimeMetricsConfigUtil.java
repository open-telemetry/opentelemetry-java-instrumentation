/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetricsBuilder;
import java.util.function.Function;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RuntimeMetricsConfigUtil {
  private RuntimeMetricsConfigUtil() {}

  @Nullable
  public static RuntimeMetrics configure(
      RuntimeMetricsBuilder builder,
      OpenTelemetry openTelemetry,
      boolean isDefaultEnabled,
      Function<String, Boolean> isModuleEnabledExplicitly) {
    /*
    By default, don't use any JFR metrics. May change this once semantic conventions are updated.
    If enabled, default to only the metrics not already covered by runtime-telemetry-java8
    */
    if (DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry_java17")
        .getBoolean("enable_all", false)) {
      builder.enableAllFeatures();
    } else if (Boolean.TRUE.equals(isModuleEnabledExplicitly.apply("runtime_telemetry_java17"))) {
      // default configuration
    } else {
      Boolean explicit = isModuleEnabledExplicitly.apply("runtime_telemetry");
      if (explicit || isDefaultEnabled) {
        // This only uses metrics gathered by JMX
        builder.disableAllFeatures();
      } else {
        // nothing is enabled
        return null;
      }
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
