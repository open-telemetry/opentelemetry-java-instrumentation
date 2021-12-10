/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics;

import application.io.opentelemetry.api.common.Attributes;
import application.io.opentelemetry.api.metrics.LongHistogram;
import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;

final class ApplicationLongHistogram implements LongHistogram {

  private final io.opentelemetry.api.metrics.LongHistogram agentHistogram;

  ApplicationLongHistogram(io.opentelemetry.api.metrics.LongHistogram agentHistogram) {
    this.agentHistogram = agentHistogram;
  }

  @Override
  public void record(long value) {
    agentHistogram.record(value);
  }

  @Override
  public void record(long value, Attributes applicationAttributes) {
    agentHistogram.record(value, Bridging.toAgent(applicationAttributes));
  }

  @Override
  public void record(long value, Attributes applicationAttributes, Context applicationContext) {
    agentHistogram.record(
        value,
        Bridging.toAgent(applicationAttributes),
        AgentContextStorage.getAgentContext(applicationContext));
  }
}
