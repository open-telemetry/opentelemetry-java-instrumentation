/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.BoundDoubleUpDownCounter;
import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;

final class ApplicationBoundDoubleUpDownCounter implements BoundDoubleUpDownCounter {

  private final io.opentelemetry.api.metrics.BoundDoubleUpDownCounter agentCounter;

  ApplicationBoundDoubleUpDownCounter(
      io.opentelemetry.api.metrics.BoundDoubleUpDownCounter agentCounter) {
    this.agentCounter = agentCounter;
  }

  @Override
  public void add(double value) {
    agentCounter.add(value);
  }

  @Override
  public void add(double value, Context context) {
    agentCounter.add(value, AgentContextStorage.getAgentContext(context));
  }

  @Override
  public void unbind() {
    agentCounter.unbind();
  }
}
