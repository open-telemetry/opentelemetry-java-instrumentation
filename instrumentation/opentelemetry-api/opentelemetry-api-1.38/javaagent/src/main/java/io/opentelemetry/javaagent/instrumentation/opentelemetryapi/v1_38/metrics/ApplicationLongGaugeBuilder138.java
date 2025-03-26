/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics;

import application.io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongGaugeBuilder;

public class ApplicationLongGaugeBuilder138 extends ApplicationLongGaugeBuilder {

  private final io.opentelemetry.api.metrics.LongGaugeBuilder agentBuilder;

  protected ApplicationLongGaugeBuilder138(
      io.opentelemetry.api.metrics.LongGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongGauge build() {
    io.opentelemetry.api.metrics.LongGauge agentLongGauge = agentBuilder.build();
    return new ApplicationLongGauge138(agentLongGauge);
  }
}
