/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.ExtendedLongCounterBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongCounterBuilder;
import java.util.List;

final class ApplicationLongCounterBuilder131 extends ApplicationLongCounterBuilder
    implements application.io.opentelemetry.extension.incubator.metrics.ExtendedLongCounterBuilder {

  private final LongCounterBuilder agentBuilder;

  ApplicationLongCounterBuilder131(LongCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleCounterBuilder ofDoubles() {
    return new ApplicationDoubleCounterBuilder131(agentBuilder.ofDoubles());
  }

  @Override
  public application.io.opentelemetry.extension.incubator.metrics.ExtendedLongCounterBuilder
      setAttributesAdvice(
          List<application.io.opentelemetry.api.common.AttributeKey<?>> attributes) {
    ((ExtendedLongCounterBuilder) agentBuilder).setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
