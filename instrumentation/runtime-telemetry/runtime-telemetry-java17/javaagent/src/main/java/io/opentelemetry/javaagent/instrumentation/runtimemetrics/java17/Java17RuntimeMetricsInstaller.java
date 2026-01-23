/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.runtimemetrics.java17;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetricsBuilder;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.RuntimeMetricsConfigUtil;
import io.opentelemetry.javaagent.bootstrap.internal.EnabledInstrumentations;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

/** An {@link AgentListener} that enables runtime metrics during agent startup. */
@AutoService(AgentListener.class)
public class Java17RuntimeMetricsInstaller implements AgentListener {

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    RuntimeMetricsBuilder builder = RuntimeMetrics.builder(openTelemetry);
    EnabledInstrumentations enabledInstrumentations =
        AgentDistributionConfig.get().getEnabledInstrumentations();
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
        return;
      }
    }

    RuntimeMetrics runtimeMetrics =
        RuntimeMetricsConfigUtil.configure(builder, openTelemetry).build();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(runtimeMetrics::close, "OpenTelemetry RuntimeMetricsShutdownHook"));
  }
}
