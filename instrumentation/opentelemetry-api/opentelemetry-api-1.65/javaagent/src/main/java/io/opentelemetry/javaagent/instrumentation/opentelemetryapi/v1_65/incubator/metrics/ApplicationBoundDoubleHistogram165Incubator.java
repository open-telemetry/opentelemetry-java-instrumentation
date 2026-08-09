/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundDoubleHistogram165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleHistogram {

  private final DoubleHistogram agentHistogram;
  private final Attributes attributes;

  ApplicationBoundDoubleHistogram165Incubator(
      DoubleHistogram agentHistogram, Attributes attributes) {
    this.agentHistogram = agentHistogram;
    this.attributes = attributes;
  }

  @Override
  public void record(double value) {
    agentHistogram.record(value, attributes);
  }

  @Override
  public void record(
      double value, application.io.opentelemetry.context.Context applicationContext) {
    agentHistogram.record(
        value, attributes, AgentContextStorage.getAgentContext(applicationContext));
  }
}
