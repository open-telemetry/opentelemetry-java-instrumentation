/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.oshi.ProcessMetrics;
import io.opentelemetry.instrumentation.oshi.SystemMetrics;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MetricsRegistration {

  private static final AtomicBoolean registered = new AtomicBoolean();

  public static void register() {
    if (registered.compareAndSet(false, true)) {
      SystemMetrics.registerObservers(GlobalOpenTelemetry.get());

      // ProcessMetrics don't follow the spec
      if (Config.get()
          .getBoolean("otel.instrumentation.oshi.experimental-metrics.enabled", false)) {
        ProcessMetrics.registerObservers(GlobalOpenTelemetry.get());
      }
    }
  }

  private MetricsRegistration() {}
}
