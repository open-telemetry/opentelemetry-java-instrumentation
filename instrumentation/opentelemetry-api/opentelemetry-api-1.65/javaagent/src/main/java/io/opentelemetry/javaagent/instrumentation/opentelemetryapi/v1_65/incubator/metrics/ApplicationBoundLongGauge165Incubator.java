/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundLongGauge165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongGauge {

  private final LongGauge agentGauge;
  private final Attributes attributes;

  ApplicationBoundLongGauge165Incubator(LongGauge agentGauge, Attributes attributes) {
    this.agentGauge = agentGauge;
    this.attributes = attributes;
  }

  @Override
  public void set(long value) {
    agentGauge.set(value, attributes);
  }

  @Override
  public void set(long value, application.io.opentelemetry.context.Context applicationContext) {
    agentGauge.set(value, attributes, AgentContextStorage.getAgentContext(applicationContext));
  }
}
