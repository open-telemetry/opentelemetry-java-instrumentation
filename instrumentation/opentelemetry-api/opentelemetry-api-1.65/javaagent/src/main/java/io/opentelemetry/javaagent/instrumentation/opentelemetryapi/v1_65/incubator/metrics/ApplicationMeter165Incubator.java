/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.BaseApplicationMeter137;

// extends BaseApplicationMeter137 directly instead of the 1.40 incubator meter: the 1.40
// incubator meter's builder methods construct 1.40 incubator builders whose build() methods
// construct 1.40 incubator marker instrument classes, which do not implement the bind(Attributes)
// methods added to the Extended*Counter/Histogram/Gauge interfaces in 1.65, and would otherwise
// make this class fail muzzle validation
final class ApplicationMeter165Incubator extends BaseApplicationMeter137 {

  private final Meter agentMeter;

  ApplicationMeter165Incubator(Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongCounterBuilder counterBuilder(String name) {
    return new ApplicationLongCounterBuilder165Incubator(agentMeter.counterBuilder(name));
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder upDownCounterBuilder(
      String name) {
    return new ApplicationLongUpDownCounterBuilder165Incubator(
        agentMeter.upDownCounterBuilder(name));
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleHistogramBuilder histogramBuilder(
      String name) {
    return new ApplicationDoubleHistogramBuilder165Incubator(agentMeter.histogramBuilder(name));
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleGaugeBuilder gaugeBuilder(String name) {
    return new ApplicationDoubleGaugeBuilder165Incubator(agentMeter.gaugeBuilder(name));
  }
}
