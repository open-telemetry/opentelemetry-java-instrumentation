/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.BoundLongUpDownCounter;
import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;

final class ApplicationBoundLongUpDownCounter implements BoundLongUpDownCounter {

  private final io.opentelemetry.api.metrics.BoundLongUpDownCounter agentCounter;

  ApplicationBoundLongUpDownCounter(
      io.opentelemetry.api.metrics.BoundLongUpDownCounter agentCounter) {
    this.agentCounter = agentCounter;
  }

  @Override
  public void add(long value) {
    agentCounter.add(value);
  }

  @Override
  public void add(long value, Context context) {
    agentCounter.add(value, AgentContextStorage.getAgentContext(context));
  }

  @Override
  public void unbind() {
    agentCounter.unbind();
  }
}
