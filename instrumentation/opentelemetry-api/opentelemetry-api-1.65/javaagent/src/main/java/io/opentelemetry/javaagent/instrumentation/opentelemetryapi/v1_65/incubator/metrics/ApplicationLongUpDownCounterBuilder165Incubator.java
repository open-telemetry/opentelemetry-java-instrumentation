/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.ApplicationLongUpDownCounterBuilder137;

// extends the 1.37 incubator builder directly instead of the 1.40 incubator builder: the 1.40
// incubator builder's build() method constructs the 1.40 incubator marker instrument class,
// which does not implement ExtendedLongUpDownCounter#bind(Attributes) (added in 1.65) and would
// otherwise make this class fail muzzle validation
final class ApplicationLongUpDownCounterBuilder165Incubator
    extends ApplicationLongUpDownCounterBuilder137 {

  private final LongUpDownCounterBuilder agentBuilder;

  ApplicationLongUpDownCounterBuilder165Incubator(LongUpDownCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder ofDoubles() {
    return new ApplicationDoubleUpDownCounterBuilder165Incubator(agentBuilder.ofDoubles());
  }

  @Override
  public application.io.opentelemetry.api.metrics.LongUpDownCounter build() {
    return new ApplicationLongUpDownCounter165Incubator(agentBuilder.build());
  }
}
