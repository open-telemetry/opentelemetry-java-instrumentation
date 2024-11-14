/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleHistogram;
import application.io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.ApplicationDoubleHistogramBuilder137;

final class ApplicationDoubleHistogramBuilder140Incubator
    extends ApplicationDoubleHistogramBuilder137 {

  private final io.opentelemetry.api.metrics.DoubleHistogramBuilder agentBuilder;

  ApplicationDoubleHistogramBuilder140Incubator(DoubleHistogramBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongHistogramBuilder ofLongs() {
    return new ApplicationLongHistogramBuilder140Incubator(agentBuilder.ofLongs());
  }

  @Override
  public DoubleHistogram build() {
    return new ApplicationDoubleHistogram140Incubator(agentBuilder.build());
  }
}
