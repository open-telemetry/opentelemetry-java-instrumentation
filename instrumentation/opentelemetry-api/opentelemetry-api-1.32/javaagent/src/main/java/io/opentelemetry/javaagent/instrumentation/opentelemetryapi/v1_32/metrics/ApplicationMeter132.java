/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_32.metrics;

import application.io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics.ApplicationMeter115;

public class ApplicationMeter132 extends ApplicationMeter115 {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  public ApplicationMeter132(io.opentelemetry.api.metrics.Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public DoubleHistogramBuilder histogramBuilder(String name) {
    return new ApplicationDoubleHistogramBuilder132(agentMeter.histogramBuilder(name));
  }
}
