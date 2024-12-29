/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics.java17;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetrics;
import io.opentelemetry.instrumentation.runtimemetrics.java17.RuntimeMetricsBuilder;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

/** An {@link AgentListener} that enables runtime metrics during agent startup. */
@AutoService(AgentListener.class)
public class Java17RuntimeMetricsInstaller implements AgentListener {

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    ConfigProperties config = AgentListener.resolveConfigProperties(autoConfiguredSdk);

    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    RuntimeMetricsBuilder builder = null;
    /*
    By default don't use any JFR metrics. May change this once semantic conventions are updated.
    If enabled, default to only the metrics not already covered by runtime-telemetry-java8
    */
    boolean defaultEnabled = config.getBoolean("otel.instrumentation.common.default-enabled", true);
    if (config.getBoolean("otel.instrumentation.runtime-telemetry-java17.enable-all", false)) {
      builder = RuntimeMetrics.builder(openTelemetry).enableAllFeatures();
    } else if (config.getBoolean("otel.instrumentation.runtime-telemetry-java17.enabled", false)) {
      builder = RuntimeMetrics.builder(openTelemetry);
    } else if (config.getBoolean(
        "otel.instrumentation.runtime-telemetry.enabled", defaultEnabled)) {
      // This only uses metrics gathered by JMX
      builder = RuntimeMetrics.builder(openTelemetry).disableAllFeatures();
    }

    if (builder != null) {
      if (config.getBoolean(
          "otel.instrumentation.runtime-telemetry.emit-experimental-telemetry", false)) {
        builder.enableExperimentalJmxTelemetry();
      }

      RuntimeMetrics finalJfrTelemetry = builder.build();
      Thread cleanupTelemetry = new Thread(() -> finalJfrTelemetry.close());
      Runtime.getRuntime().addShutdownHook(cleanupTelemetry);
    }
  }
}
