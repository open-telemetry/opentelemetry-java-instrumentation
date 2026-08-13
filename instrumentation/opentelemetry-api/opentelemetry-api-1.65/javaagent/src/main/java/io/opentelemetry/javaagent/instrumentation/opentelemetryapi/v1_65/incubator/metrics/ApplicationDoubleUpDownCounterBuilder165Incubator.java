/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_37.incubator.metrics.ApplicationDoubleUpDownCounterBuilder137;

// extends the 1.37 incubator builder directly instead of the 1.40 incubator builder: the 1.40
// incubator builder's build() method constructs the 1.40 incubator marker instrument class,
// which does not implement ExtendedDoubleUpDownCounter#bind(Attributes) (added in 1.65) and
// would otherwise make this class fail muzzle validation
final class ApplicationDoubleUpDownCounterBuilder165Incubator
    extends ApplicationDoubleUpDownCounterBuilder137 {

  private final DoubleUpDownCounterBuilder agentBuilder;

  ApplicationDoubleUpDownCounterBuilder165Incubator(DoubleUpDownCounterBuilder agentBuilder) {
    super(agentBuilder);
    this.agentBuilder = agentBuilder;
  }

  @Override
  public application.io.opentelemetry.api.metrics.DoubleUpDownCounter build() {
    return new ApplicationDoubleUpDownCounter165Incubator(agentBuilder.build());
  }
}
