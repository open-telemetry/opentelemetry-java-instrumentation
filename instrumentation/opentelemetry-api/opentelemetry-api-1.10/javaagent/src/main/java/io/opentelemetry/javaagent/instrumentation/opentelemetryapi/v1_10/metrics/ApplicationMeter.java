/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.Meter;

public class ApplicationMeter implements application.io.opentelemetry.api.metrics.Meter {

  private final Meter agentMeter;

  protected ApplicationMeter(Meter agentMeter) {
    this.agentMeter = agentMeter;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongCounterBuilder counterBuilder(String name) {
    return new ApplicationLongCounterBuilder(agentMeter.counterBuilder(name));
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder upDownCounterBuilder(
      String name) {
    return new ApplicationLongUpDownCounterBuilder(agentMeter.upDownCounterBuilder(name));
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleHistogramBuilder histogramBuilder(
      String name) {
    return new ApplicationDoubleHistogramBuilder(agentMeter.histogramBuilder(name));
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleGaugeBuilder gaugeBuilder(String name) {
    return new ApplicationDoubleGaugeBuilder(agentMeter.gaugeBuilder(name));
  }
}
