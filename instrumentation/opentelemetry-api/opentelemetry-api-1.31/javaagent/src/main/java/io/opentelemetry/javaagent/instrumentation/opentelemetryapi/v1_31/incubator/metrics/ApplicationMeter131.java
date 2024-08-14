/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import application.io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import application.io.opentelemetry.api.metrics.LongCounterBuilder;
import application.io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongUpDownCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics.ApplicationMeter115;

public class ApplicationMeter131 extends ApplicationMeter115 {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  protected ApplicationMeter131(io.opentelemetry.api.metrics.Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public LongCounterBuilder counterBuilder(String name) {
    io.opentelemetry.api.metrics.LongCounterBuilder builder = agentMeter.counterBuilder(name);
    if (builder instanceof io.opentelemetry.api.incubator.metrics.ExtendedLongCounterBuilder) {
      return new ApplicationLongCounterBuilder131(builder);
    }
    return new ApplicationLongCounterBuilder(builder);
  }

  @Override
  public LongUpDownCounterBuilder upDownCounterBuilder(String name) {
    io.opentelemetry.api.metrics.LongUpDownCounterBuilder builder =
        agentMeter.upDownCounterBuilder(name);
    if (builder
        instanceof io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounterBuilder) {
      return new ApplicationLongUpDownCounterBuilder131(builder);
    }
    return new ApplicationLongUpDownCounterBuilder(builder);
  }

  @Override
  public DoubleHistogramBuilder histogramBuilder(String name) {
    io.opentelemetry.api.metrics.DoubleHistogramBuilder builder = agentMeter.histogramBuilder(name);
    if (builder instanceof io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder) {
      return new ApplicationDoubleHistogramBuilder131(builder);
    }
    return new ApplicationDoubleHistogramBuilder(builder);
  }

  @Override
  public DoubleGaugeBuilder gaugeBuilder(String name) {
    io.opentelemetry.api.metrics.DoubleGaugeBuilder builder = agentMeter.gaugeBuilder(name);
    if (builder instanceof io.opentelemetry.api.incubator.metrics.ExtendedDoubleGaugeBuilder) {
      return new ApplicationDoubleGaugeBuilder131(builder);
    }
    return new ApplicationDoubleGaugeBuilder(builder);
  }
}
