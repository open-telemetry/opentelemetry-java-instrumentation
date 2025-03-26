/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics.java8;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.java8.RuntimeMetrics;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.RuntimeMetricsConfigUtil;
import io.opentelemetry.javaagent.bootstrap.internal.AgentInstrumentationConfig;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;

/** An {@link AgentListener} that enables runtime metrics during agent startup. */
@AutoService(AgentListener.class)
public class Java8RuntimeMetricsInstaller implements AgentListener {

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    if (Double.parseDouble(System.getProperty("java.specification.version")) >= 17) {
      return;
    }

    RuntimeMetrics runtimeMetrics =
        RuntimeMetricsConfigUtil.configure(
            RuntimeMetrics.builder(GlobalOpenTelemetry.get()), AgentInstrumentationConfig.get());
    if (runtimeMetrics != null) {
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(runtimeMetrics::close, "OpenTelemetry RuntimeMetricsShutdownHook"));
    }
  }
}
