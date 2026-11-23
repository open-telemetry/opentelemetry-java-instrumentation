/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Metrics;
import java.util.concurrent.atomic.AtomicLong;

public final class GaugeRegistrar {

  private static final AtomicLong VALUE = new AtomicLong(42);

  public static void register(String metricName) {
    Gauge.builder(metricName, VALUE, AtomicLong::doubleValue).register(Metrics.globalRegistry);
  }

  private GaugeRegistrar() {}
}
