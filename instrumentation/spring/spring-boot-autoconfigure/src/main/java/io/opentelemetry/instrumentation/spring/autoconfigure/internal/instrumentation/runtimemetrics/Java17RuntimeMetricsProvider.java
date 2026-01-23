/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.runtimemetrics;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.incubator.config.EnabledInstrumentations;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetricsBuilder;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.RuntimeMetricsConfigUtil;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configures runtime metrics collection for Java 17+.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public class Java17RuntimeMetricsProvider implements RuntimeMetricsProvider {
  private static final Logger logger = LoggerFactory.getLogger(Java17RuntimeMetricsProvider.class);

  @Override
  public int minJavaVersion() {
    return 17;
  }

  @Nullable
  @Override
  public AutoCloseable start(
      OpenTelemetry openTelemetry, EnabledInstrumentations enabledInstrumentations) {
    logger.debug("Use runtime metrics instrumentation for Java 17+");
    RuntimeMetricsBuilder builder = RuntimeMetrics.builder(openTelemetry);

    if (RuntimeMetricsConfigUtil.allEnabled(openTelemetry)) {
      builder.enableAllFeatures();
    } else if (enabledInstrumentations.isEnabledExplicitly("runtime-telemetry-java17")) {
      // default configuration
    } else {
      if (enabledInstrumentations.isEnabled("runtime-telemetry")) {
        // This only uses metrics gathered by JMX
        builder.disableAllFeatures();
      } else {
        // nothing is enabled
        return null;
      }
    }

    return RuntimeMetricsConfigUtil.configure(builder, openTelemetry).build();
  }
}
