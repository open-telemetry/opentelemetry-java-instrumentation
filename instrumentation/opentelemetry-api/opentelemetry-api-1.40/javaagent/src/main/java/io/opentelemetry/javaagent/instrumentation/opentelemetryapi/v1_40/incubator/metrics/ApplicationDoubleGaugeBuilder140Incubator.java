/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_40.incubator.metrics;

import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.incubator.metrics.ApplicationDoubleGaugeBuilder138Incubator;

final class ApplicationDoubleGaugeBuilder140Incubator
    extends ApplicationDoubleGaugeBuilder138Incubator {

  private final DoubleGaugeBuilder agentBuilder;

  ApplicationDoubleGaugeBuilder140Incubator(DoubleGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongGaugeBuilder ofLongs() {
    return new ApplicationLongGaugeBuilder140Incubator(agentBuilder.ofLongs());
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleGauge build() {
    return new ApplicationDoubleGauge140Incubator(agentBuilder.build());
  }
}
