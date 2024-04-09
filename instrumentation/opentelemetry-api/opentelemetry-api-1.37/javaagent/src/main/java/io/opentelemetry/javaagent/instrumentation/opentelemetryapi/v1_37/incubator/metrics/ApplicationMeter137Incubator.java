/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.metrics.ApplicationMeter131;

class ApplicationMeter137Incubator extends ApplicationMeter131 {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  ApplicationMeter137Incubator(io.opentelemetry.api.metrics.Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public DoubleHistogramBuilder histogramBuilder(String name) {
    return new ApplicationDoubleHistogramBuilder137Incubator(agentMeter.histogramBuilder(name));
  }
}
