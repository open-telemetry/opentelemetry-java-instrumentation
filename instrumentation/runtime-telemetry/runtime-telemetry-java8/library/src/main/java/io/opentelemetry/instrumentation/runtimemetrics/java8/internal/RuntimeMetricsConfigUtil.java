/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8.internal;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.api.incubator.config.internal.DeclarativeConfigUtil;
import io.opentelemetry.instrumentation.runtimemetrics.java8.RuntimeMetrics;
import io.opentelemetry.instrumentation.runtimemetrics.java8.RuntimeMetricsBuilder;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RuntimeMetricsConfigUtil {

  private static final Logger logger = Logger.getLogger(RuntimeMetricsConfigUtil.class.getName());

  private RuntimeMetricsConfigUtil() {}

  @Nullable
  public static RuntimeMetrics configure(
      RuntimeMetricsBuilder builder, OpenTelemetry openTelemetry, String instrumentationMode) {
    DeclarativeConfigProperties config =
        DeclarativeConfigUtil.getInstrumentationConfig(openTelemetry, "runtime_telemetry");
    if (!config.getBoolean("enabled", instrumentationMode.equals("default"))) {
      // nothing is enabled
      return null;
    }

    if (config.getBoolean("emit_experimental_telemetry/development", false)) {
      builder.emitExperimentalTelemetry();
    }

    if (config.getBoolean("capture_gc_cause", false)) {
      logger.warning(
          "The capture_gc_cause configuration option is deprecated and will be removed in 3.0."
              + " Prefer using metric views to enable the jvm.gc.cause attribute."
              + " See https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/instrumentation/runtime-telemetry/README.md"
              + " for more information.");
      RuntimeMetricsBuilderInternal.captureGcCause(builder);
    }

    return builder.build();
  }
}
