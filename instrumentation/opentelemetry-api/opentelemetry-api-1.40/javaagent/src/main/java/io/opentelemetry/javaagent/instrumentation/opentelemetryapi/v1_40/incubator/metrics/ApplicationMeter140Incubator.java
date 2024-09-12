/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import application.io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import application.io.opentelemetry.api.metrics.LongCounterBuilder;
import application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.BaseApplicationMeter137;

final class ApplicationMeter140Incubator extends BaseApplicationMeter137 {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  ApplicationMeter140Incubator(io.opentelemetry.api.metrics.Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public LongCounterBuilder counterBuilder(String name) {
    return new ApplicationLongCounterBuilder140Incubator(agentMeter.counterBuilder(name));
  }

  @Override
  public LongUpDownCounterBuilder upDownCounterBuilder(String name) {
    return new ApplicationLongUpDownCounterBuilder140Incubator(
        agentMeter.upDownCounterBuilder(name));
  }

  @Override
  public DoubleHistogramBuilder histogramBuilder(String name) {
    return new ApplicationDoubleHistogramBuilder140Incubator(agentMeter.histogramBuilder(name));
  }

  @Override
  public DoubleGaugeBuilder gaugeBuilder(String name) {
    return new ApplicationDoubleGaugeBuilder140Incubator(agentMeter.gaugeBuilder(name));
  }
}
