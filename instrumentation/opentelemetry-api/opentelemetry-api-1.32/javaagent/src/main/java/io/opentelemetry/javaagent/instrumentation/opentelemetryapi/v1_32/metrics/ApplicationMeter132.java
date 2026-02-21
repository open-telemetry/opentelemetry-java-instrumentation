/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_32.metrics;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_15.metrics.ApplicationMeter115;

public class ApplicationMeter132 extends ApplicationMeter115 {

  private final Meter agentMeter;

  public ApplicationMeter132(Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleHistogramBuilder histogramBuilder(
      String name) {
    return new ApplicationDoubleHistogramBuilder132(agentMeter.histogramBuilder(name));
  }
}
