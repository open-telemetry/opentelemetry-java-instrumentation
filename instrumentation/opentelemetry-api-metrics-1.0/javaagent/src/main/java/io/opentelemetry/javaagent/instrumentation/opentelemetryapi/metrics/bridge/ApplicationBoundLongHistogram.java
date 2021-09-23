/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.metrics.bridge;

import application.io.opentelemetry.api.metrics.BoundLongHistogram;
import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;

final class ApplicationBoundLongHistogram implements BoundLongHistogram {

  private final io.opentelemetry.api.metrics.BoundLongHistogram agentHistogram;

  ApplicationBoundLongHistogram(io.opentelemetry.api.metrics.BoundLongHistogram agentHistogram) {
    this.agentHistogram = agentHistogram;
  }

  @Override
  public void record(long value) {
    agentHistogram.record(value);
  }

  @Override
  public void record(long value, Context context) {
    agentHistogram.record(value, AgentContextStorage.getAgentContext(context));
  }

  @Override
  public void unbind() {
    agentHistogram.unbind();
  }
}
