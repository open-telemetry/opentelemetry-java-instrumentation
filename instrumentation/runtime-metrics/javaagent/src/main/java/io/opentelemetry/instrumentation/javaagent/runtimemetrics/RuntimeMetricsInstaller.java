/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetrics.BufferPools;
import io.opentelemetry.instrumentation.runtimemetrics.Classes;
import io.opentelemetry.instrumentation.runtimemetrics.Cpu;
import io.opentelemetry.instrumentation.runtimemetrics.GarbageCollector;
import io.opentelemetry.instrumentation.runtimemetrics.MemoryPools;
import io.opentelemetry.instrumentation.runtimemetrics.Threads;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import java.util.Locale;

/** An {@link AgentListener} that enables runtime metrics during agent startup. */
@AutoService(AgentListener.class)
public class RuntimeMetricsInstaller implements AgentListener {

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    ConfigProperties config = autoConfiguredSdk.getConfig();

    boolean defaultEnabled =
        config.getBoolean(normalize("otel.instrumentation.common.default-enabled"), true);
    if (!config.getBoolean(
        normalize("otel.instrumentation.runtime-metrics.enabled"), defaultEnabled)) {
      return;
    }

    Classes.registerObservers(GlobalOpenTelemetry.get());
    Cpu.registerObservers(GlobalOpenTelemetry.get());
    MemoryPools.registerObservers(GlobalOpenTelemetry.get());
    Threads.registerObservers(GlobalOpenTelemetry.get());

    if (config.getBoolean(
        normalize("otel.instrumentation.runtime-metrics.experimental-metrics.enabled"), false)) {
      GarbageCollector.registerObservers(GlobalOpenTelemetry.get());
      BufferPools.registerObservers(GlobalOpenTelemetry.get());
    }
  }

  // TODO: remove after https://github.com/open-telemetry/opentelemetry-java/issues/4562 is fixed
  private static String normalize(String key) {
    return key.toLowerCase(Locale.ROOT).replace('-', '.');
  }
}
