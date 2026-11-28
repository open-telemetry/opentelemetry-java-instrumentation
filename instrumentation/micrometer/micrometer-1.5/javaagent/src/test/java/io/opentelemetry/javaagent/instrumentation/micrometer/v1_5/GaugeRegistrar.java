/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.TimeGauge;
import java.util.concurrent.atomic.AtomicLong;

public final class GaugeRegistrar {

  private static final AtomicLong VALUE = new AtomicLong(42);

  public static void register(String metricName, boolean useGlobalRegistry) {
    // The global composite keeps its own strong reference to the gauge function.
    MeterRegistry registry =
        useGlobalRegistry
            ? Metrics.globalRegistry
            : Metrics.globalRegistry.getRegistries().iterator().next();
    Gauge.builder(metricName, VALUE, AtomicLong::doubleValue).register(registry);
  }

  public static void registerTimeGauge(String metricName) {
    MeterRegistry registry = Metrics.globalRegistry.getRegistries().iterator().next();
    TimeGauge.builder(metricName, VALUE, MILLISECONDS, AtomicLong::doubleValue).register(registry);
  }

  private GaugeRegistrar() {}
}
