/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.LongGaugeBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.incubator.metrics.ApplicationLongGaugeBuilder138Incubator;

// extends the 1.38 incubator builder directly instead of the 1.40 incubator builder: the 1.40
// incubator builder's build() method constructs the 1.40 incubator marker instrument class,
// which does not implement ExtendedLongGauge#bind(Attributes) (added in 1.65) and would
// otherwise make this class fail muzzle validation
final class ApplicationLongGaugeBuilder165Incubator
    extends ApplicationLongGaugeBuilder138Incubator {

  private final LongGaugeBuilder agentBuilder;

  ApplicationLongGaugeBuilder165Incubator(LongGaugeBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongGauge build() {
    return new ApplicationLongGauge165Incubator(agentBuilder.build());
  }
}
