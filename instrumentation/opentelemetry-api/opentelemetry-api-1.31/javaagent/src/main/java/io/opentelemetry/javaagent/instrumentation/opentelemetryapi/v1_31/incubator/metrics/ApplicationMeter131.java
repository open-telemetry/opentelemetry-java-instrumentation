/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics.ApplicationMeter115;

public class ApplicationMeter131 extends ApplicationMeter115 {

  private final Meter agentMeter;

  protected ApplicationMeter131(Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongCounterBuilder counterBuilder(String name) {
    return new ApplicationLongCounterBuilder131(agentMeter.counterBuilder(name));
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder upDownCounterBuilder(
      String name) {
    return new ApplicationLongUpDownCounterBuilder131(agentMeter.upDownCounterBuilder(name));
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleHistogramBuilder histogramBuilder(
      String name) {
    return new ApplicationDoubleHistogramBuilder131(agentMeter.histogramBuilder(name));
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleGaugeBuilder gaugeBuilder(String name) {
    return new ApplicationDoubleGaugeBuilder131(agentMeter.gaugeBuilder(name));
  }
}
