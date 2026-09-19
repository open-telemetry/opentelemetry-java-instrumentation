/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.ApplicationDoubleCounterBuilder137;

// extends the 1.37 incubator builder directly instead of the 1.40 incubator builder: the 1.40
// incubator builder's build() method constructs the 1.40 incubator marker instrument class,
// which does not implement ExtendedDoubleCounter#bind(Attributes) (added in 1.65) and would
// otherwise make this class fail muzzle validation
final class ApplicationDoubleCounterBuilder165Incubator extends ApplicationDoubleCounterBuilder137 {

  private final DoubleCounterBuilder agentBuilder;

  ApplicationDoubleCounterBuilder165Incubator(DoubleCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleCounter build() {
    return new ApplicationDoubleCounter165Incubator(agentBuilder.build());
  }
}
