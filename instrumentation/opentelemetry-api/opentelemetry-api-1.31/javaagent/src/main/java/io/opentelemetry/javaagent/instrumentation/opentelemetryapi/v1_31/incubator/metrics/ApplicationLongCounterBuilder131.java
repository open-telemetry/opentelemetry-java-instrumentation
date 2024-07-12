/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics;

import application.io.opentelemetry.api.common.AttributeKey;
import application.io.opentelemetry.api.metrics.DoubleCounterBuilder;
import application.io.opentelemetry.extension.incubator.metrics.ExtendedLongCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongCounterBuilder;
import java.util.List;

final class ApplicationLongCounterBuilder131 extends ApplicationLongCounterBuilder
    implements ExtendedLongCounterBuilder {

  private final io.opentelemetry.api.metrics.LongCounterBuilder agentBuilder;

  ApplicationLongCounterBuilder131(io.opentelemetry.api.metrics.LongCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public DoubleCounterBuilder ofDoubles() {
    return new ApplicationDoubleCounterBuilder131(agentBuilder.ofDoubles());
  }

  @Override
  public ExtendedLongCounterBuilder setAttributesAdvice(List<AttributeKey<?>> attributes) {
    ((io.opentelemetry.api.incubator.metrics.ExtendedLongCounterBuilder) agentBuilder)
        .setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
