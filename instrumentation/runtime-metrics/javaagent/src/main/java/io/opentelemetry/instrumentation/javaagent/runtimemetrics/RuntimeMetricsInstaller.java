/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.javaagent.runtimemetrics;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.runtimemetrics.GarbageCollector;
import io.opentelemetry.instrumentation.runtimemetrics.MemoryPools;
import io.opentelemetry.javaagent.extension.AgentListener;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import java.util.Collections;

/** An {@link AgentListener} that enables runtime metrics during agent startup. */
@AutoService(AgentListener.class)
public class RuntimeMetricsInstaller implements AgentListener {
  @Override
  public void afterAgent(Config config, AutoConfiguredOpenTelemetrySdk unused) {
    if (config.isInstrumentationEnabled(
        Collections.singleton("runtime-metrics"), /* defaultEnabled= */ true)) {
      GarbageCollector.registerObservers();
      MemoryPools.registerObservers(GlobalOpenTelemetry.get());
    }
  }
}
