/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.BoundLongUpDownCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundLongUpDownCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongUpDownCounter {

  private final BoundLongUpDownCounter agentBoundCounter;

  ApplicationBoundLongUpDownCounter165Incubator(BoundLongUpDownCounter agentBoundCounter) {
    this.agentBoundCounter = agentBoundCounter;
  }

  @Override
  public void add(long value) {
    agentBoundCounter.add(value);
  }

  @Override
  public void add(long value, application.io.opentelemetry.context.Context applicationContext) {
    agentBoundCounter.add(value, AgentContextStorage.getAgentContext(applicationContext));
  }
}
