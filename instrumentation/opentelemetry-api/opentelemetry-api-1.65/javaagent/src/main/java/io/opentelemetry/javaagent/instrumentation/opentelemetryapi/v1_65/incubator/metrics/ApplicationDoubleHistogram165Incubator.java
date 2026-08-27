/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleHistogram;

// extends the plain (non-incubator) bridge directly instead of the 1.40 incubator marker class,
// since the 1.40 incubator marker class does not implement
// ExtendedDoubleHistogram#bind(Attributes) (added in 1.65) and would otherwise make this class
// fail muzzle validation
final class ApplicationDoubleHistogram165Incubator extends ApplicationDoubleHistogram
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogram {

  private final DoubleHistogram agentHistogram;

  ApplicationDoubleHistogram165Incubator(DoubleHistogram agentHistogram) {
    super(agentHistogram);
    this.agentHistogram = agentHistogram;
  }

  @Override
  public application.io.opentelemetry.api.incubator.metrics.BoundDoubleHistogram bind(
      application.io.opentelemetry.api.common.Attributes applicationAttributes) {
    return ApplicationBoundDoubleHistogram165Incubator.create(
        agentHistogram, Bridging.toAgent(applicationAttributes));
  }
}
