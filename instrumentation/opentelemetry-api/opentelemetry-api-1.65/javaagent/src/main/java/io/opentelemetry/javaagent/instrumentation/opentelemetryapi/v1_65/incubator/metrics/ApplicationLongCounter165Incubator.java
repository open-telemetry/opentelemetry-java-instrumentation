/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationLongCounter;

// extends the plain (non-incubator) bridge directly instead of the 1.40 incubator marker class,
// since the 1.40 incubator marker class does not implement ExtendedLongCounter#bind(Attributes)
// (added in 1.65) and would otherwise make this class fail muzzle validation
final class ApplicationLongCounter165Incubator extends ApplicationLongCounter
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedLongCounter {

  private final LongCounter agentCounter;

  ApplicationLongCounter165Incubator(LongCounter agentCounter) {
    super(agentCounter);
    this.agentCounter = agentCounter;
  }

  @Override
  public application.io.opentelemetry.api.incubator.metrics.BoundLongCounter bind(
      application.io.opentelemetry.api.common.Attributes applicationAttributes) {
    return ApplicationBoundLongCounter165Incubator.create(
        agentCounter, Bridging.toAgent(applicationAttributes));
  }
}
