/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetricsjava8;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.runtimemetricsjava8.BufferPools;
import io.opentelemetry.instrumentation.runtimemetricsjava8.Classes;
import io.opentelemetry.instrumentation.runtimemetricsjava8.Cpu;
import io.opentelemetry.instrumentation.runtimemetricsjava8.GarbageCollector;
import io.opentelemetry.instrumentation.runtimemetricsjava8.MemoryPools;
import io.opentelemetry.instrumentation.runtimemetricsjava8.Threads;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;

/** An {@link AgentListener} that enables runtime metrics during agent startup. */
@AutoService(AgentListener.class)
public class Java8RuntimeMetricsInstaller implements AgentListener {

  @Override
  public void afterAgent(AutoConfiguredOpenTelemetrySdk autoConfiguredSdk) {
    ConfigProperties config = autoConfiguredSdk.getConfig();

    boolean defaultEnabled = config.getBoolean("otel.instrumentation.common.default-enabled", true);
    if (!config.getBoolean("otel.instrumentation.runtime-metrics-java8.enabled", defaultEnabled)
        || Double.parseDouble(System.getProperty("java.specification.version")) >= 17) {
      return;
    }
    OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();

    BufferPools.registerObservers(openTelemetry);
    Classes.registerObservers(openTelemetry);
    Cpu.registerObservers(openTelemetry);
    MemoryPools.registerObservers(openTelemetry);
    Threads.registerObservers(openTelemetry);
    GarbageCollector.registerObservers(openTelemetry);
    Thread cleanupTelemetry =
        new Thread(
            () -> {
              BufferPools.closeObservers();
              Classes.closeObservers();
              Cpu.closeObservers();
              MemoryPools.closeObservers();
              Threads.closeObservers();
            });
    Runtime.getRuntime().addShutdownHook(cleanupTelemetry);
  }
}
