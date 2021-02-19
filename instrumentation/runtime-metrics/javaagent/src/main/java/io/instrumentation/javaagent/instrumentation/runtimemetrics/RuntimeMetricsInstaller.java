/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.instrumentation.javaagent.instrumentation.runtimemetrics;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.runtimemetrics.GarbageCollector;
import io.opentelemetry.instrumentation.runtimemetrics.MemoryPools;
import io.opentelemetry.javaagent.spi.ComponentInstaller;
import java.util.Collections;

/** {@link ComponentInstaller} to enable runtime metrics during agent startup. */
@AutoService(ComponentInstaller.class)
public class RuntimeMetricsInstaller implements ComponentInstaller {
  @Override
  public void afterByteBuddyAgent() {
    if (Config.get().isInstrumentationEnabled(Collections.singleton("runtime-metrics"), true)) {
      GarbageCollector.registerObservers();
      MemoryPools.registerObservers();
    }
  }
}
