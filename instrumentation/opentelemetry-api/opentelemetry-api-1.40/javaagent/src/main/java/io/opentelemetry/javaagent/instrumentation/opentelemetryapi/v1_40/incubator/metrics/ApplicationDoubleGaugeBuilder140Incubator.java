/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleGauge;
import application.io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.incubator.metrics.ApplicationDoubleGaugeBuilder138Incubator;

final class ApplicationDoubleGaugeBuilder140Incubator
    extends ApplicationDoubleGaugeBuilder138Incubator {

  private final io.opentelemetry.api.metrics.DoubleGaugeBuilder agentBuilder;

  ApplicationDoubleGaugeBuilder140Incubator(DoubleGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public LongGaugeBuilder ofLongs() {
    return new ApplicationLongGaugeBuilder140Incubator(agentBuilder.ofLongs());
  }

  @Override
  public DoubleGauge build() {
    return new ApplicationDoubleGauge140Incubator(agentBuilder.build());
  }
}
