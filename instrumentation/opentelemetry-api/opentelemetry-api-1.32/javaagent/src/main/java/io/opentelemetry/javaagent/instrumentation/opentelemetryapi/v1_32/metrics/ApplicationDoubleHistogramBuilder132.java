/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_32.metrics;

import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleHistogramBuilder;
import java.util.List;

class ApplicationDoubleHistogramBuilder132 extends ApplicationDoubleHistogramBuilder {

  private final DoubleHistogramBuilder agentBuilder;

  ApplicationDoubleHistogramBuilder132(DoubleHistogramBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongHistogramBuilder ofLongs() {
    return new ApplicationLongHistogramBuilder132(agentBuilder.ofLongs());
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleHistogramBuilder
      setExplicitBucketBoundariesAdvice(List<Double> bucketBoundaries) {
    agentBuilder.setExplicitBucketBoundariesAdvice(bucketBoundaries);
    return this;
  }
}
