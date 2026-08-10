/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongUpDownCounter;

// extends the plain (non-incubator) bridge directly instead of the 1.40 incubator marker class,
// since the 1.40 incubator marker class does not implement
// ExtendedLongUpDownCounter#bind(Attributes) (added in 1.65) and would otherwise make this class
// fail muzzle validation
final class ApplicationLongUpDownCounter165Incubator extends ApplicationLongUpDownCounter
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounter {

  private final LongUpDownCounter agentCounter;

  ApplicationLongUpDownCounter165Incubator(LongUpDownCounter agentCounter) {
    super(agentCounter);
    this.agentCounter = agentCounter;
  }

  @Override
  public application.io.opentelemetry.api.incubator.metrics.BoundLongUpDownCounter bind(
      application.io.opentelemetry.api.common.Attributes applicationAttributes) {
    return ApplicationBoundLongUpDownCounter165Incubator.create(
        agentCounter, Bridging.toAgent(applicationAttributes));
  }
}
