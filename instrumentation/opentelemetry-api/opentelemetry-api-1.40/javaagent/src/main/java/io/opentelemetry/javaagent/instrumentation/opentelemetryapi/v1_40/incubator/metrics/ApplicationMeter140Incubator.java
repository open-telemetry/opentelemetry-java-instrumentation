/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.BaseApplicationMeter137;

final class ApplicationMeter140Incubator extends BaseApplicationMeter137 {

  private final Meter agentMeter;

  ApplicationMeter140Incubator(Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongCounterBuilder counterBuilder(String name) {
    return new ApplicationLongCounterBuilder140Incubator(agentMeter.counterBuilder(name));
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder upDownCounterBuilder(
      String name) {
    return new ApplicationLongUpDownCounterBuilder140Incubator(
        agentMeter.upDownCounterBuilder(name));
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleHistogramBuilder histogramBuilder(
      String name) {
    return new ApplicationDoubleHistogramBuilder140Incubator(agentMeter.histogramBuilder(name));
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleGaugeBuilder gaugeBuilder(String name) {
    return new ApplicationDoubleGaugeBuilder140Incubator(agentMeter.gaugeBuilder(name));
  }
}
