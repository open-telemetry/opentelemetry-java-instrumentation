/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundDoubleUpDownCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleUpDownCounter {

  private final DoubleUpDownCounter agentCounter;
  private final Attributes attributes;

  ApplicationBoundDoubleUpDownCounter165Incubator(
      DoubleUpDownCounter agentCounter, Attributes attributes) {
    this.agentCounter = agentCounter;
    this.attributes = attributes;
  }

  @Override
  public void add(double value) {
    agentCounter.add(value, attributes);
  }

  @Override
  public void add(double value, application.io.opentelemetry.context.Context applicationContext) {
    agentCounter.add(value, attributes, AgentContextStorage.getAgentContext(applicationContext));
  }
}
