/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.BoundLongGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundLongGauge165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongGauge {

  private final BoundLongGauge agentBoundGauge;

  ApplicationBoundLongGauge165Incubator(BoundLongGauge agentBoundGauge) {
    this.agentBoundGauge = agentBoundGauge;
  }

  @Override
  public void set(long value) {
    agentBoundGauge.set(value);
  }

  @Override
  public void set(long value, application.io.opentelemetry.context.Context applicationContext) {
    agentBoundGauge.set(value, AgentContextStorage.getAgentContext(applicationContext));
  }
}
