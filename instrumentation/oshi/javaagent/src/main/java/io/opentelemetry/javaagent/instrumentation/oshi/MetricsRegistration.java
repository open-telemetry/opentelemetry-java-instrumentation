package io.opentelemetry.javaagent.instrumentation.oshi;

import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.instrumentation.oshi.ProcessMetrics;
import io.opentelemetry.instrumentation.oshi.SystemMetrics;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MetricsRegistration {

  private static final AtomicBoolean registered = new AtomicBoolean();

  public static void register() {
    if (registered.compareAndSet(false, true)) {
      SystemMetrics.registerObservers();

      // ProcessMetrics don't follow the spec
      if (Config.get()
          .getBoolean("otel.instrumentation.oshi.experimental-metrics.enabled", false)) {
        ProcessMetrics.registerObservers();
      }
    }
  }

  private MetricsRegistration() {}
}
