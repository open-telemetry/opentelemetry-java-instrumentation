/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics.ApplicationLongGauge138;

// extends the plain (non-incubator) bridge directly instead of the 1.40 incubator marker class,
// since the 1.40 incubator marker class does not implement ExtendedLongGauge#bind(Attributes)
// (added in 1.65) and would otherwise make this class fail muzzle validation
final class ApplicationLongGauge165Incubator extends ApplicationLongGauge138
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedLongGauge {

  private final LongGauge agentLongGauge;

  ApplicationLongGauge165Incubator(LongGauge agentLongGauge) {
    super(agentLongGauge);
    this.agentLongGauge = agentLongGauge;
  }

  @Override
  public application.io.opentelemetry.api.incubator.metrics.BoundLongGauge bind(
      application.io.opentelemetry.api.common.Attributes applicationAttributes) {
    return ApplicationBoundLongGauge165Incubator.create(
        agentLongGauge, Bridging.toAgent(applicationAttributes));
  }
}
