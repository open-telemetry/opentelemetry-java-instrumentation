/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.metrics.LongHistogram;
import application.io.opentelemetry.api.metrics.LongHistogramBuilder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

final class ApplicationLongHistogramBuilder implements LongHistogramBuilder {

  private final io.opentelemetry.api.metrics.LongHistogramBuilder agentBuilder;

  ApplicationLongHistogramBuilder(io.opentelemetry.api.metrics.LongHistogramBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public LongHistogramBuilder setDescription(String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public LongHistogramBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public LongHistogram build() {
    return new ApplicationLongHistogram(agentBuilder.build());
  }
}
