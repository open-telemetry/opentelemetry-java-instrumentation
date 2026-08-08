/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.BoundDoubleHistogram;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundDoubleHistogram165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleHistogram {

  private final BoundDoubleHistogram agentBoundHistogram;

  ApplicationBoundDoubleHistogram165Incubator(BoundDoubleHistogram agentBoundHistogram) {
    this.agentBoundHistogram = agentBoundHistogram;
  }

  @Override
  public void record(double value) {
    agentBoundHistogram.record(value);
  }

  @Override
  public void record(
      double value, application.io.opentelemetry.context.Context applicationContext) {
    agentBoundHistogram.record(value, AgentContextStorage.getAgentContext(applicationContext));
  }
}
