/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleUpDownCounter;

// extends the plain (non-incubator) bridge directly instead of the 1.40 incubator marker class,
// since the 1.40 incubator marker class does not implement
// ExtendedDoubleUpDownCounter#bind(Attributes) (added in 1.65) and would otherwise make this
// class fail muzzle validation
final class ApplicationDoubleUpDownCounter165Incubator extends ApplicationDoubleUpDownCounter
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounter {

  private final DoubleUpDownCounter agentCounter;

  ApplicationDoubleUpDownCounter165Incubator(DoubleUpDownCounter agentCounter) {
    super(agentCounter);
    this.agentCounter = agentCounter;
  }

  @Override
  public application.io.opentelemetry.api.incubator.metrics.BoundDoubleUpDownCounter bind(
      application.io.opentelemetry.api.common.Attributes applicationAttributes) {
    return ApplicationBoundDoubleUpDownCounter165Incubator.create(
        agentCounter, Bridging.toAgent(applicationAttributes));
  }
}
