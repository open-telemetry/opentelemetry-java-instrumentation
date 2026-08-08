/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.BoundLongCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundLongCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongCounter {

  private final BoundLongCounter agentBoundCounter;

  ApplicationBoundLongCounter165Incubator(BoundLongCounter agentBoundCounter) {
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
