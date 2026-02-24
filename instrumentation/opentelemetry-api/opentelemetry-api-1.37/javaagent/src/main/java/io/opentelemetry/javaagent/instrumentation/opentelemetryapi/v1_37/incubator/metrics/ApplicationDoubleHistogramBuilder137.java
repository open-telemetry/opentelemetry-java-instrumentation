/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import application.io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleHistogramBuilder;
import java.util.List;

public class ApplicationDoubleHistogramBuilder137 extends ApplicationDoubleHistogramBuilder
    implements ExtendedDoubleHistogramBuilder {

  private final io.opentelemetry.api.metrics.DoubleHistogramBuilder agentBuilder;

  protected ApplicationDoubleHistogramBuilder137(
      io.opentelemetry.api.metrics.DoubleHistogramBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongHistogramBuilder ofLongs() {
    return new ApplicationLongHistogramBuilder137(agentBuilder.ofLongs());
  }

  @Override
  public ExtendedDoubleHistogramBuilder setExplicitBucketBoundariesAdvice(
      List<Double> bucketBoundaries) {
    agentBuilder.setExplicitBucketBoundariesAdvice(bucketBoundaries);
    return this;
  }

  @Override
  public ExtendedDoubleHistogramBuilder setAttributesAdvice(List<AttributeKey<?>> attributes) {
    ((io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder) agentBuilder)
        .setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
