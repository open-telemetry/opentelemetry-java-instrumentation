/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import application.io.opentelemetry.api.metrics.LongCounterBuilder;
import application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics.ApplicationMeter115;

// used by both 1.37 and 1.38
public class BaseApplicationMeter137 extends ApplicationMeter115 {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  protected BaseApplicationMeter137(io.opentelemetry.api.metrics.Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public LongCounterBuilder counterBuilder(String name) {
    return new ApplicationLongCounterBuilder137(agentMeter.counterBuilder(name));
  }

  @Override
  public LongUpDownCounterBuilder upDownCounterBuilder(String name) {
    return new ApplicationLongUpDownCounterBuilder137(agentMeter.upDownCounterBuilder(name));
  }

  @Override
  public DoubleHistogramBuilder histogramBuilder(String name) {
    return new ApplicationDoubleHistogramBuilder137(agentMeter.histogramBuilder(name));
  }
}
