/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleHistogramBuilder;
import java.util.List;

public class ApplicationDoubleHistogramBuilder137 extends ApplicationDoubleHistogramBuilder
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder {

  private final DoubleHistogramBuilder agentBuilder;

  protected ApplicationDoubleHistogramBuilder137(DoubleHistogramBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongHistogramBuilder ofLongs() {
    return new ApplicationLongHistogramBuilder137(agentBuilder.ofLongs());
  }

  @Override
  public application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder
      setExplicitBucketBoundariesAdvice(List<Double> bucketBoundaries) {
    agentBuilder.setExplicitBucketBoundariesAdvice(bucketBoundaries);
    return this;
  }

  @Override
  public application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder
      setAttributesAdvice(
          List<application.io.opentelemetry.api.common.AttributeKey<?>> attributes) {
    ((ExtendedDoubleHistogramBuilder) agentBuilder)
        .setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
