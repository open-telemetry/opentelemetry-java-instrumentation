/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import application.io.opentelemetry.api.metrics.LongHistogram;
import application.io.opentelemetry.api.metrics.LongHistogramBuilder;

final class ApplicationLongHistogramBuilder implements LongHistogramBuilder {

  private final io.opentelemetry.api.metrics.LongHistogramBuilder agentBuilder;

  ApplicationLongHistogramBuilder(io.opentelemetry.api.metrics.LongHistogramBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongHistogramBuilder setDescription(String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  public LongHistogramBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public DoubleHistogramBuilder ofDoubles() {
    return new ApplicationDoubleHistogramBuilder(agentBuilder.ofDoubles());
  }

  @Override
  public LongHistogram build() {
    return new ApplicationLongHistogram(agentBuilder.build());
  }
}
