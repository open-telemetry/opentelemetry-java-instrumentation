/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal;

import io.opentelemetry.instrumentation.api.incubator.config.internal.InstrumentationConfig;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetricsBuilder;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RuntimeMetricsConfigUtil {
  private RuntimeMetricsConfigUtil() {}

  @Nullable
  public static RuntimeMetrics configure(
      RuntimeMetricsBuilder builder, InstrumentationConfig config) {
    /*
    By default, don't use any JFR metrics. May change this once semantic conventions are updated.
    If enabled, default to only the metrics not already covered by runtime-telemetry-java8
    */
    boolean defaultEnabled = config.getBoolean("otel.instrumentation.common.default-enabled", true);
    if (config.getBoolean("otel.instrumentation.runtime-telemetry-java17.enable-all", false)) {
      builder.enableAllFeatures();
    } else if (config.getBoolean("otel.instrumentation.runtime-telemetry-java17.enabled", false)) {
      // default configuration
    } else if (config.getBoolean(
        "otel.instrumentation.runtime-telemetry.enabled", defaultEnabled)) {
      // This only uses metrics gathered by JMX
      builder.disableAllFeatures();
    } else {
      // nothing is enabled
      return null;
    }

    if (config.getBoolean(
        "otel.instrumentation.runtime-telemetry.emit-experimental-telemetry", false)) {
      builder.enableExperimentalJmxTelemetry();
    }

    return builder.build();
  }
}
