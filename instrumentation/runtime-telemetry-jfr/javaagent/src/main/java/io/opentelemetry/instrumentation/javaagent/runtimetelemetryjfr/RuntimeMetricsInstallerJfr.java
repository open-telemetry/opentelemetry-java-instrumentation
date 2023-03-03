/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimetelemetryjfr;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.JfrTelemetry;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

/** An {@link AgentListener} that enables runtime metrics during agent startup. */
@AutoService(AgentListener.class)
public class RuntimeMetricsInstallerJfr implements AgentListener {

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    ConfigProperties config = autoConfiguredSdk.getConfig();

    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
    /*
    By default don't use any JFR metrics. May change this once semantic conventions are updated.
    If enabled, default to only the metrics not already covered by runtime-telemetry-jmx
    */
    if (config.getBoolean("otel.instrumentation.runtime-telemetry-jfr.enable-all", false)) {
      JfrTelemetry.builder(openTelemetry).enableAllFeatures().build();
    } else if (config.getBoolean("otel.instrumentation.runtime-telemetry-jfr.enabled", false)) {
      JfrTelemetry.create(openTelemetry);
    }
  }
}
