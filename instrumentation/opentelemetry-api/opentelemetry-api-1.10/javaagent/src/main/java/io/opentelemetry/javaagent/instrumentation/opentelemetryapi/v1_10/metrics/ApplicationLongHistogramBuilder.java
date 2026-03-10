/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.metrics.LongHistogramBuilder;

public class ApplicationLongHistogramBuilder
    implements application.io.opentelemetry.api.metrics.LongHistogramBuilder {

  private final LongHistogramBuilder agentBuilder;

  protected ApplicationLongHistogramBuilder(LongHistogramBuilder agentBuilder) {
    this.agentBuilder = agentBuilder;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.LongHistogramBuilder setDescription(
      String description) {
    agentBuilder.setDescription(description);
    return this;
  }

  @Override
  @CanIgnoreReturnValue
  public application.io.opentelemetry.api.metrics.LongHistogramBuilder setUnit(String unit) {
    agentBuilder.setUnit(unit);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongHistogram build() {
    return new ApplicationLongHistogram(agentBuilder.build());
  }
}
