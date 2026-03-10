/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.ExtendedLongGaugeBuilder;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongGaugeBuilder;
import java.util.List;

final class ApplicationLongGaugeBuilder131 extends ApplicationLongGaugeBuilder
    implements application.io.opentelemetry.extension.incubator.metrics.ExtendedLongGaugeBuilder {

  private final LongGaugeBuilder agentBuilder;

  ApplicationLongGaugeBuilder131(LongGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.extension.incubator.metrics.LongGauge build() {
    LongGauge agentLongGauge = agentBuilder.build();
    return new application.io.opentelemetry.extension.incubator.metrics.LongGauge() {
      @Override
      public void set(long value) {
        agentLongGauge.set(value);
      }

      @Override
      public void set(long value, application.io.opentelemetry.api.common.Attributes attributes) {
        agentLongGauge.set(value, Bridging.toAgent(attributes));
      }
    };
  }

  @Override
  public application.io.opentelemetry.extension.incubator.metrics.ExtendedLongGaugeBuilder
      setAttributesAdvice(
          List<application.io.opentelemetry.api.common.AttributeKey<?>> attributes) {
    ((ExtendedLongGaugeBuilder) agentBuilder).setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
