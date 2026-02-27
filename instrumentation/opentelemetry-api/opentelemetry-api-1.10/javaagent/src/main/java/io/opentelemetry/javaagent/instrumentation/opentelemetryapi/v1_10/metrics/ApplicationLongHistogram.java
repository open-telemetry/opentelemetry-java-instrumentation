/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;

public class ApplicationLongHistogram
    implements application.io.opentelemetry.api.metrics.LongHistogram {

  private final LongHistogram agentHistogram;

  protected ApplicationLongHistogram(LongHistogram agentHistogram) {
    this.agentHistogram = agentHistogram;
  }

  @Override
  public void record(long value) {
    agentHistogram.record(value);
  }

  @Override
  public void record(
      long value, application.io.opentelemetry.api.common.Attributes applicationAttributes) {
    agentHistogram.record(value, Bridging.toAgent(applicationAttributes));
  }

  @Override
  public void record(
      long value,
      application.io.opentelemetry.api.common.Attributes applicationAttributes,
      application.io.opentelemetry.context.Context applicationContext) {
    agentHistogram.record(
        value,
        Bridging.toAgent(applicationAttributes),
        AgentContextStorage.getAgentContext(applicationContext));
  }
}
