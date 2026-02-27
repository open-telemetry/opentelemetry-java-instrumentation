/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.ExtendedDoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleGaugeBuilder;
import java.util.List;

final class ApplicationDoubleGaugeBuilder131 extends ApplicationDoubleGaugeBuilder
    implements application.io.opentelemetry.extension.incubator.metrics.ExtendedDoubleGaugeBuilder {

  private final DoubleGaugeBuilder agentBuilder;

  ApplicationDoubleGaugeBuilder131(DoubleGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongGaugeBuilder ofLongs() {
    return new ApplicationLongGaugeBuilder131(agentBuilder.ofLongs());
  }

  @Override
  public application.io.opentelemetry.extension.incubator.metrics.DoubleGauge build() {
    DoubleGauge agentDoubleGauge = agentBuilder.build();
    return new application.io.opentelemetry.extension.incubator.metrics.DoubleGauge() {

      @Override
      public void set(double value) {
        agentDoubleGauge.set(value);
      }

      @Override
      public void set(double value, application.io.opentelemetry.api.common.Attributes attributes) {
        agentDoubleGauge.set(value, Bridging.toAgent(attributes));
      }
    };
  }

  @Override
  public application.io.opentelemetry.extension.incubator.metrics.ExtendedDoubleGaugeBuilder
      setAttributesAdvice(
          List<application.io.opentelemetry.api.common.AttributeKey<?>> attributes) {
    ((ExtendedDoubleGaugeBuilder) agentBuilder).setAttributesAdvice(Bridging.toAgent(attributes));
    return this;
  }
}
