/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.oshi.v5_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.oshi.v5_0.SystemMetrics;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class MetricsRegistration {

  private static final AtomicBoolean registered = new AtomicBoolean();

  public static void register() {
    if (registered.compareAndSet(false, true)) {
      List<AutoCloseable> observables = SystemMetrics.registerObservers(GlobalOpenTelemetry.get());
      Thread cleanupTelemetry = new Thread(() -> MetricsRegistration.closeObservables(observables));
      Runtime.getRuntime().addShutdownHook(cleanupTelemetry);
    }
  }

  private static void closeObservables(List<AutoCloseable> observables) {
    observables.forEach(MetricsRegistration::closeObservable);
  }

  private static void closeObservable(AutoCloseable observable) {
    try {
      observable.close();
    } catch (Exception e) {
      throw new IllegalStateException("Error occurred closing observable", e);
    }
  }

  private MetricsRegistration() {}
}
