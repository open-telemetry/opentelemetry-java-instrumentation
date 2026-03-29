/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_32.incubator.metrics;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics.ApplicationMeter131;

class ApplicationMeter132Incubator extends ApplicationMeter131 {

  private final Meter agentMeter;

  ApplicationMeter132Incubator(Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleHistogramBuilder histogramBuilder(
      String name) {
    return new ApplicationDoubleHistogramBuilder132Incubator(agentMeter.histogramBuilder(name));
  }
}
