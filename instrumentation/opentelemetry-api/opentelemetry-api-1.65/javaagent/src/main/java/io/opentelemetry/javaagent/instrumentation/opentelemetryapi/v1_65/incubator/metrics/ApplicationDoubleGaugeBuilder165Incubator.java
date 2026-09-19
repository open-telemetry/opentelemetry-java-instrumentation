/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.incubator.metrics.ApplicationDoubleGaugeBuilder138Incubator;

// extends the 1.38 incubator builder directly instead of the 1.40 incubator builder: the 1.40
// incubator builder's build() method constructs the 1.40 incubator marker instrument class,
// which does not implement ExtendedDoubleGauge#bind(Attributes) (added in 1.65) and would
// otherwise make this class fail muzzle validation
final class ApplicationDoubleGaugeBuilder165Incubator
    extends ApplicationDoubleGaugeBuilder138Incubator {

  private final DoubleGaugeBuilder agentBuilder;

  ApplicationDoubleGaugeBuilder165Incubator(DoubleGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongGaugeBuilder ofLongs() {
    return new ApplicationLongGaugeBuilder165Incubator(agentBuilder.ofLongs());
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleGauge build() {
    return new ApplicationDoubleGauge165Incubator(agentBuilder.build());
  }
}
