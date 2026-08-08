/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.incubator.metrics.BoundLongHistogram;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundLongHistogram165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongHistogram {

  private final BoundLongHistogram agentBoundHistogram;

  ApplicationBoundLongHistogram165Incubator(BoundLongHistogram agentBoundHistogram) {
    this.agentBoundHistogram = agentBoundHistogram;
  }

  @Override
  public void record(long value) {
    agentBoundHistogram.record(value);
  }

  @Override
  public void record(long value, application.io.opentelemetry.context.Context applicationContext) {
    agentBoundHistogram.record(value, AgentContextStorage.getAgentContext(applicationContext));
  }
}
