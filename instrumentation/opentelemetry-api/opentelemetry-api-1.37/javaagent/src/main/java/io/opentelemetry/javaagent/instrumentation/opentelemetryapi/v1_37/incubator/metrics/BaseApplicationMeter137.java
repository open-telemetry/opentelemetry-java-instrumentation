/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics.ApplicationMeter115;

// used by both 1.37 and 1.38
public class BaseApplicationMeter137 extends ApplicationMeter115 {

  private final Meter agentMeter;

  protected BaseApplicationMeter137(Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongCounterBuilder counterBuilder(String name) {
    return new ApplicationLongCounterBuilder137(agentMeter.counterBuilder(name));
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder upDownCounterBuilder(
      String name) {
    return new ApplicationLongUpDownCounterBuilder137(agentMeter.upDownCounterBuilder(name));
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleHistogramBuilder histogramBuilder(
      String name) {
    return new ApplicationDoubleHistogramBuilder137(agentMeter.histogramBuilder(name));
  }
}
