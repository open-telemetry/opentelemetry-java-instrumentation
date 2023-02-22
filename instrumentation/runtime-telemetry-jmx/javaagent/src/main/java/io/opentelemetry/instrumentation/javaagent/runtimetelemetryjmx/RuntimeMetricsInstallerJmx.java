/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimetelemetryjmx;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimetelemetryjmx.BufferPools;
import io.opentelemetry.instrumentation.runtimetelemetryjmx.Classes;
import io.opentelemetry.instrumentation.runtimetelemetryjmx.Cpu;
import io.opentelemetry.instrumentation.runtimetelemetryjmx.GarbageCollector;
import io.opentelemetry.instrumentation.runtimetelemetryjmx.MemoryPools;
import io.opentelemetry.instrumentation.runtimetelemetryjmx.Threads;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

/** An {@link AgentListener} that enables runtime metrics during agent startup. */
@AutoService(AgentListener.class)
public class RuntimeMetricsInstallerJmx implements AgentListener {

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    ConfigProperties config = autoConfiguredSdk.getConfig();

    boolean defaultEnabled = config.getBoolean("otel.instrumentation.common.default-enabled", true);
    if (!config.getBoolean("otel.instrumentation.runtime-telemetry-jmx.enabled", defaultEnabled)) {
      return;
    }

    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();

    BufferPools.registerObservers(openTelemetry);
    Classes.registerObservers(openTelemetry);
    Cpu.registerObservers(openTelemetry);
    MemoryPools.registerObservers(openTelemetry);
    Threads.registerObservers(openTelemetry);
    GarbageCollector.registerObservers(openTelemetry);
  }
}
