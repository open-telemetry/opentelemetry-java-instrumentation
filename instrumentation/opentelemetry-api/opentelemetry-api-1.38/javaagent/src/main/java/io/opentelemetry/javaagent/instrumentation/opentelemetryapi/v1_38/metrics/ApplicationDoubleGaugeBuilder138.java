/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics;

import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleGaugeBuilder;

public class ApplicationDoubleGaugeBuilder138 extends ApplicationDoubleGaugeBuilder {

  private final DoubleGaugeBuilder agentBuilder;

  protected ApplicationDoubleGaugeBuilder138(DoubleGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongGaugeBuilder ofLongs() {
    return new ApplicationLongGaugeBuilder138(agentBuilder.ofLongs());
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleGauge build() {
    DoubleGauge agentDoubleGauge = agentBuilder.build();
    return new ApplicationDoubleGauge138(agentDoubleGauge);
  }
}
