/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.DoubleHistogram;
import application.io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import application.io.opentelemetry.api.metrics.LongHistogramBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

public class ApplicationDoubleHistogramBuilder implements DoubleHistogramBuilder {

  private final io.opentelemetry.api.metrics.DoubleHistogramBuilder agentBuilder;

  protected ApplicationDoubleHistogramBuilder(
      io.opentelemetry.api.metrics.DoubleHistogramBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public DoubleHistogramBuilder setDescription(String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public DoubleHistogramBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public LongHistogramBuilder ofLongs() {
    return new ApplicationLongHistogramBuilder(agentBuilder.ofLongs());
  }

  @Override
  public DoubleHistogram build() {
    return new ApplicationDoubleHistogram(agentBuilder.build());
  }
}
