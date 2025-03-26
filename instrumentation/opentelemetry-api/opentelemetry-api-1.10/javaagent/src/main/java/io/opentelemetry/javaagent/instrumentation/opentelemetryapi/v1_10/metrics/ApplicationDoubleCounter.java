/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.metrics.DoubleCounter;
import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;

public class ApplicationDoubleCounter implements DoubleCounter {

  private final io.opentelemetry.api.metrics.DoubleCounter agentCounter;

  protected ApplicationDoubleCounter(io.opentelemetry.api.metrics.DoubleCounter agentCounter) {
    this.agentCounter = agentCounter;
  }

  @Override
  public void add(double value) {
    agentCounter.add(value);
  }

  @Override
  public void add(double value, Attributes applicationAttributes) {
    agentCounter.add(value, Bridging.toAgent(applicationAttributes));
  }

  @Override
  public void add(double value, Attributes applicationAttributes, Context applicationContext) {
    agentCounter.add(
        value,
        Bridging.toAgent(applicationAttributes),
        AgentContextStorage.getAgentContext(applicationContext));
  }
}
