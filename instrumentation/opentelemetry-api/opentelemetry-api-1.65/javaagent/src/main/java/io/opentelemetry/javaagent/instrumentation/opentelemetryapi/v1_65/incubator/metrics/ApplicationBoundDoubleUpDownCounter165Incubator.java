/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundDoubleUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounter;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundDoubleUpDownCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleUpDownCounter {

  private final BoundDoubleUpDownCounter delegate;

  static ApplicationBoundDoubleUpDownCounter165Incubator create(
      DoubleUpDownCounter agentCounter, Attributes attributes) {
    return new ApplicationBoundDoubleUpDownCounter165Incubator(
        ((ExtendedDoubleUpDownCounter) agentCounter).bind(attributes));
  }

  private ApplicationBoundDoubleUpDownCounter165Incubator(BoundDoubleUpDownCounter delegate) {
    this.delegate = delegate;
  }

  @Override
  public void add(double value) {
    delegate.add(value);
  }

  @Override
  public void add(double value, application.io.opentelemetry.context.Context applicationContext) {
    delegate.add(value, AgentContextStorage.getAgentContext(applicationContext));
  }
}
