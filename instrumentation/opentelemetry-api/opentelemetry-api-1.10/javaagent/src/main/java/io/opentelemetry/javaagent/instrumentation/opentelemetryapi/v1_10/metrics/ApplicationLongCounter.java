/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;

public class ApplicationLongCounter
    implements application.io.opentelemetry.api.metrics.LongCounter {

  private final LongCounter agentCounter;

  protected ApplicationLongCounter(LongCounter agentCounter) {
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
