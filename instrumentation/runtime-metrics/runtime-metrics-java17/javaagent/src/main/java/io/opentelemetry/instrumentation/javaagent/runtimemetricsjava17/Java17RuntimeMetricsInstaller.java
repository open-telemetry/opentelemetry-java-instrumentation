/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetricsjava17;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetricsjava17.RuntimeMetrics;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

/** An {@link AgentListener} that enables runtime metrics during agent startup. */
@AutoService(AgentListener.class)
public class Java17RuntimeMetricsInstaller implements AgentListener {

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    ConfigProperties config = autoConfiguredSdk.getConfig();

    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    RuntimeMetrics runtimeMetrics = null;
    /*
    By default don't use any JFR metrics. May change this once semantic conventions are updated.
    If enabled, default to only the metrics not already covered by runtime-metrics-java8
    */

    if (config.getBoolean("otel.instrumentation.runtime-metrics-java17.enable-all", false)) {
      runtimeMetrics = RuntimeMetrics.builder(openTelemetry).enableAllFeatures().build();
    } else if (config.getBoolean("otel.instrumentation.runtime-metrics-java17.enabled", false)) {
      runtimeMetrics = RuntimeMetrics.create(openTelemetry);
    } else {
      // This only uses metrics gathered by JMX
      runtimeMetrics = RuntimeMetrics.builder(openTelemetry).disableAllFeatures().build();
    }
    if (runtimeMetrics != null) {
      RuntimeMetrics finalJfrTelemetry = runtimeMetrics;
      Thread cleanupTelemetry = new Thread(() -> finalJfrTelemetry.close());
      Runtime.getRuntime().addShutdownHook(cleanupTelemetry);
    }
  }
}
