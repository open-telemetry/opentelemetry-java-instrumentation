/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongUpDownCounterBuilder;
import java.util.List;

final class ApplicationLongUpDownCounterBuilder131 extends ApplicationLongUpDownCounterBuilder
    implements application.io.opentelemetry.extension.incubator.metrics
        .ExtendedLongUpDownCounterBuilder {

  private final LongUpDownCounterBuilder agentBuilder;

  ApplicationLongUpDownCounterBuilder131(LongUpDownCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder ofDoubles() {
    return new ApplicationDoubleUpDownCounterBuilder131(agentBuilder.ofDoubles());
  }

  @Override
  public application.io.opentelemetry.extension.incubator.metrics.ExtendedLongUpDownCounterBuilder
      setAttributesAdvice(
          List<application.io.opentelemetry.api.common.AttributeKey<?>> attributes) {
    ((ExtendedLongUpDownCounterBuilder) agentBuilder)
        .setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
