/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.metrics;

import application.io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleHistogramBuilder;
import java.util.List;

class ApplicationDoubleHistogramBuilder132 extends ApplicationDoubleHistogramBuilder {

  private final io.opentelemetry.api.metrics.DoubleHistogramBuilder agentBuilder;

  ApplicationDoubleHistogramBuilder132(
      io.opentelemetry.api.metrics.DoubleHistogramBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  // return type is different from what 1.31 uses
  public DoubleHistogramBuilder setExplicitBucketBoundariesAdvice(List<Double> bucketBoundaries) {
    agentBuilder.setExplicitBucketBoundariesAdvice(bucketBoundaries);
    return this;
  }
}
