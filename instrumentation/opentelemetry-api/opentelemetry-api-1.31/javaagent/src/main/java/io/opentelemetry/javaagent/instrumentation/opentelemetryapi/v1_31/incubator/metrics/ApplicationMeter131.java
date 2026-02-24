/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import application.io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import application.io.opentelemetry.api.metrics.LongCounterBuilder;
import application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics.ApplicationMeter115;

public class ApplicationMeter131 extends ApplicationMeter115 {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  protected ApplicationMeter131(io.opentelemetry.api.metrics.Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public LongCounterBuilder counterBuilder(String name) {
    return new ApplicationLongCounterBuilder131(agentMeter.counterBuilder(name));
  }

  @Override
  public LongUpDownCounterBuilder upDownCounterBuilder(String name) {
    return new ApplicationLongUpDownCounterBuilder131(agentMeter.upDownCounterBuilder(name));
  }

  @Override
  public DoubleHistogramBuilder histogramBuilder(String name) {
    return new ApplicationDoubleHistogramBuilder131(agentMeter.histogramBuilder(name));
  }

  @Override
  public DoubleGaugeBuilder gaugeBuilder(String name) {
    return new ApplicationDoubleGaugeBuilder131(agentMeter.gaugeBuilder(name));
  }
}
