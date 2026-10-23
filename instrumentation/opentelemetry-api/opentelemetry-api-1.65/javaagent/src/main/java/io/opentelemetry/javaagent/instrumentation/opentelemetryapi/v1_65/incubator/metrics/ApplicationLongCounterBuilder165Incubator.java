/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.ApplicationLongCounterBuilder137;

// extends the 1.37 incubator builder directly instead of the 1.40 incubator builder: the 1.40
// incubator builder's build() method constructs the 1.40 incubator marker instrument class,
// which does not implement ExtendedLongCounter#bind(Attributes) (added in 1.65) and would
// otherwise make this class fail muzzle validation
final class ApplicationLongCounterBuilder165Incubator extends ApplicationLongCounterBuilder137 {

  private final LongCounterBuilder agentBuilder;

  ApplicationLongCounterBuilder165Incubator(LongCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleCounterBuilder ofDoubles() {
    return new ApplicationDoubleCounterBuilder165Incubator(agentBuilder.ofDoubles());
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongCounter build() {
    return new ApplicationLongCounter165Incubator(agentBuilder.build());
  }
}
