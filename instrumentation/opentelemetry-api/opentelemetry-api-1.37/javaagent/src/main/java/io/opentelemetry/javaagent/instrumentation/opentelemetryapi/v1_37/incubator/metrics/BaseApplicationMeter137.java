/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import application.io.opentelemetry.api.metrics.LongCounterBuilder;
import application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongUpDownCounterBuilder;
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
    io.opentelemetry.api.metrics.LongCounterBuilder builder = agentMeter.counterBuilder(name);
    if (builder instanceof io.opentelemetry.api.incubator.metrics.ExtendedLongCounterBuilder) {
      return new ApplicationLongCounterBuilder137(builder);
    }
    return new ApplicationLongCounterBuilder(builder);
  }

  @Override
  public LongUpDownCounterBuilder upDownCounterBuilder(String name) {
    io.opentelemetry.api.metrics.LongUpDownCounterBuilder builder =
        agentMeter.upDownCounterBuilder(name);
    if (builder
        instanceof io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounterBuilder) {
      return new ApplicationLongUpDownCounterBuilder137(builder);
    }
    return new ApplicationLongUpDownCounterBuilder(builder);
  }

  @Override
  public DoubleHistogramBuilder histogramBuilder(String name) {
    io.opentelemetry.api.metrics.DoubleHistogramBuilder builder = agentMeter.histogramBuilder(name);
    if (builder instanceof io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder) {
      return new ApplicationDoubleHistogramBuilder137(builder);
    }
    return new ApplicationDoubleHistogramBuilder(builder);
  }
}
