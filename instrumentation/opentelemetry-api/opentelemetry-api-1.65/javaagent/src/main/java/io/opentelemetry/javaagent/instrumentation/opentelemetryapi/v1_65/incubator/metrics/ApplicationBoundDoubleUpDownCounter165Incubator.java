/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.BoundDoubleUpDownCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundDoubleUpDownCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleUpDownCounter {

  private final BoundDoubleUpDownCounter agentBoundCounter;

  ApplicationBoundDoubleUpDownCounter165Incubator(BoundDoubleUpDownCounter agentBoundCounter) {
    this.agentBoundCounter = agentBoundCounter;
  }

  @Override
  public void add(double value) {
    agentBoundCounter.add(value);
  }

  @Override
  public void add(double value, application.io.opentelemetry.context.Context applicationContext) {
    agentBoundCounter.add(value, AgentContextStorage.getAgentContext(applicationContext));
  }
}
