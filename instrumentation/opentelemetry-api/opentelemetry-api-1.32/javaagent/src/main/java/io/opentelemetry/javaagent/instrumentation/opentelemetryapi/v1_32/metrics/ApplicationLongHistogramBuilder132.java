/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_32.metrics;

import application.io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongHistogramBuilder;
import java.util.List;

class ApplicationLongHistogramBuilder132 extends ApplicationLongHistogramBuilder {

  private final io.opentelemetry.api.metrics.LongHistogramBuilder agentBuilder;

  ApplicationLongHistogramBuilder132(
      io.opentelemetry.api.metrics.LongHistogramBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongHistogramBuilder setExplicitBucketBoundariesAdvice(List<Long> bucketBoundaries) {
    agentBuilder.setExplicitBucketBoundariesAdvice(bucketBoundaries);
    return this;
  }
}
