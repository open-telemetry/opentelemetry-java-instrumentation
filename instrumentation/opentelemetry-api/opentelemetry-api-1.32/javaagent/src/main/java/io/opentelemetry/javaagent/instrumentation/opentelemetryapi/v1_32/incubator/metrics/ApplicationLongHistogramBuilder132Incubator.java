/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_32.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongHistogramBuilder;
import java.util.List;

class ApplicationLongHistogramBuilder132Incubator extends ApplicationLongHistogramBuilder
    implements application.io.opentelemetry.extension.incubator.metrics
        .ExtendedLongHistogramBuilder {

  private final LongHistogramBuilder agentBuilder;

  ApplicationLongHistogramBuilder132Incubator(LongHistogramBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.extension.incubator.metrics.ExtendedLongHistogramBuilder
      setExplicitBucketBoundariesAdvice(List<Long> bucketBoundaries) {
    agentBuilder.setExplicitBucketBoundariesAdvice(bucketBoundaries);
    return this;
  }

  @Override
  public application.io.opentelemetry.extension.incubator.metrics.ExtendedLongHistogramBuilder
      setAttributesAdvice(
          List<application.io.opentelemetry.api.common.AttributeKey<?>> attributes) {
    ((ExtendedLongHistogramBuilder) agentBuilder).setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
