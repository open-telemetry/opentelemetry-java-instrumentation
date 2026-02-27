/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;

public class ApplicationLongUpDownCounter
    implements application.io.opentelemetry.api.metrics.LongUpDownCounter {

  private final LongUpDownCounter agentCounter;

  protected ApplicationLongUpDownCounter(LongUpDownCounter agentCounter) {
    this.agentCounter = agentCounter;
  }

  @Override
  public void add(long value) {
    agentCounter.add(value);
  }

  @Override
  public void add(
      long value, application.io.opentelemetry.api.common.Attributes applicationAttributes) {
    agentCounter.add(value, Bridging.toAgent(applicationAttributes));
  }

  @Override
  public void add(
      long value,
      application.io.opentelemetry.api.common.Attributes applicationAttributes,
      application.io.opentelemetry.context.Context applicationContext) {
    agentCounter.add(
        value,
        Bridging.toAgent(applicationAttributes),
        AgentContextStorage.getAgentContext(applicationContext));
  }
}
