/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.BoundDoubleGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundDoubleGauge165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleGauge {

  private final BoundDoubleGauge agentBoundGauge;

  ApplicationBoundDoubleGauge165Incubator(BoundDoubleGauge agentBoundGauge) {
    this.agentBoundGauge = agentBoundGauge;
  }

  @Override
  public void set(double value) {
    agentBoundGauge.set(value);
  }

  @Override
  public void set(double value, application.io.opentelemetry.context.Context applicationContext) {
    agentBoundGauge.set(value, AgentContextStorage.getAgentContext(applicationContext));
  }
}
