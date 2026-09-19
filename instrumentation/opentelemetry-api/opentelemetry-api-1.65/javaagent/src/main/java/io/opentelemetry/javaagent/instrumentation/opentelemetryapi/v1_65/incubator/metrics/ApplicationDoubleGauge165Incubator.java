/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.trace.Bridging;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_38.metrics.ApplicationDoubleGauge138;

// extends the plain (non-incubator) bridge directly instead of the 1.40 incubator marker class,
// since the 1.40 incubator marker class does not implement ExtendedDoubleGauge#bind(Attributes)
// (added in 1.65) and would otherwise make this class fail muzzle validation
final class ApplicationDoubleGauge165Incubator extends ApplicationDoubleGauge138
    implements application.io.opentelemetry.api.incubator.metrics.ExtendedDoubleGauge {

  private final DoubleGauge agentDoubleGauge;

  ApplicationDoubleGauge165Incubator(DoubleGauge agentDoubleGauge) {
    super(agentDoubleGauge);
    this.agentDoubleGauge = agentDoubleGauge;
  }

  @Override
  public application.io.opentelemetry.api.incubator.metrics.BoundDoubleGauge bind(
      application.io.opentelemetry.api.common.Attributes applicationAttributes) {
    return ApplicationBoundDoubleGauge165Incubator.create(
        agentDoubleGauge, Bridging.toAgent(applicationAttributes));
  }
}
