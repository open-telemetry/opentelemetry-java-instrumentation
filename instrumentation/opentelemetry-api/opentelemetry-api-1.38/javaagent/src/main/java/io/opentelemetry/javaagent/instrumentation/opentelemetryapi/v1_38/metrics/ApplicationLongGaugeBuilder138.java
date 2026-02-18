/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics;

import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongGaugeBuilder;

public class ApplicationLongGaugeBuilder138 extends ApplicationLongGaugeBuilder {

  private final LongGaugeBuilder agentBuilder;

  protected ApplicationLongGaugeBuilder138(LongGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongGauge build() {
    LongGauge agentLongGauge = agentBuilder.build();
    return new ApplicationLongGauge138(agentLongGauge);
  }
}
