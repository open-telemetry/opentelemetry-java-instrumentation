/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import java.util.concurrent.atomic.AtomicLong;

public final class GaugeRegistrar {

  private static final GaugeValue VALUE = new GaugeValue();

  public static void register(String metricName) {
    Gauge.builder(metricName, VALUE, GaugeValue::value).register(Metrics.globalRegistry);
  }

  private static final class GaugeValue {

    private final AtomicLong value = new AtomicLong(42);

    private double value() {
      return value.get();
    }
  }

  private GaugeRegistrar() {}
}
