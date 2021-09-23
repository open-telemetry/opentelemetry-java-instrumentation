/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.BoundDoubleHistogram;
import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;

final class ApplicationBoundDoubleHistogram implements BoundDoubleHistogram {

  private final io.opentelemetry.api.metrics.BoundDoubleHistogram agentHistogram;

  ApplicationBoundDoubleHistogram(
      io.opentelemetry.api.metrics.BoundDoubleHistogram agentHistogram) {
    this.agentHistogram = agentHistogram;
  }

  @Override
  public void record(double value) {
    agentHistogram.record(value);
  }

  @Override
  public void record(double value, Context context) {
    agentHistogram.record(value, AgentContextStorage.getAgentContext(context));
  }

  @Override
  public void unbind() {
    agentHistogram.unbind();
  }
}
