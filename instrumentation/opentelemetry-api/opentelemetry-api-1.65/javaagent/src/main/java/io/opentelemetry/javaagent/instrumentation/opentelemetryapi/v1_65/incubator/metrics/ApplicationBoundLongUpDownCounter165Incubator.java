/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundLongUpDownCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongUpDownCounter {

  private final LongUpDownCounter agentCounter;
  private final Attributes attributes;

  ApplicationBoundLongUpDownCounter165Incubator(
      LongUpDownCounter agentCounter, Attributes attributes) {
    this.agentCounter = agentCounter;
    this.attributes = attributes;
  }

  @Override
  public void add(long value) {
    agentCounter.add(value, attributes);
  }

  @Override
  public void add(long value, application.io.opentelemetry.context.Context applicationContext) {
    agentCounter.add(value, attributes, AgentContextStorage.getAgentContext(applicationContext));
  }
}
