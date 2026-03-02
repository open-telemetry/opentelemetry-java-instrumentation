/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;

public class ApplicationDoubleHistogramBuilder
    implements application.io.opentelemetry.api.metrics.DoubleHistogramBuilder {

  private final DoubleHistogramBuilder agentBuilder;

  protected ApplicationDoubleHistogramBuilder(DoubleHistogramBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.DoubleHistogramBuilder setDescription(
      String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.DoubleHistogramBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongHistogramBuilder ofLongs() {
    return new ApplicationLongHistogramBuilder(agentBuilder.ofLongs());
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleHistogram build() {
    return new ApplicationDoubleHistogram(agentBuilder.build());
  }
}
