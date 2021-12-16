/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import application.io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import application.io.opentelemetry.api.metrics.LongCounterBuilder;
import application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import application.io.opentelemetry.api.metrics.Meter;

class ApplicationMeter implements Meter {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  ApplicationMeter(io.opentelemetry.api.metrics.Meter agentMeter) {
    this.agentMeter = agentMeter;
  }

  @Override
  public LongCounterBuilder counterBuilder(String name) {
    return new ApplicationLongCounterBuilder(agentMeter.counterBuilder(name));
  }

  @Override
  public LongUpDownCounterBuilder upDownCounterBuilder(String name) {
    return new ApplicationLongUpDownCounterBuilder(agentMeter.upDownCounterBuilder(name));
  }

  @Override
  public DoubleHistogramBuilder histogramBuilder(String name) {
    return new ApplicationDoubleHistogramBuilder(agentMeter.histogramBuilder(name));
  }

  @Override
  public DoubleGaugeBuilder gaugeBuilder(String name) {
    return new ApplicationDoubleGaugeBuilder(agentMeter.gaugeBuilder(name));
  }
}
