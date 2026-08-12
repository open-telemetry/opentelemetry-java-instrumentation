/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundDoubleCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleCounter;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundDoubleCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleCounter {

  private final BoundDoubleCounter delegate;

  static ApplicationBoundDoubleCounter165Incubator create(
      DoubleCounter agentCounter, Attributes attributes) {
    return new ApplicationBoundDoubleCounter165Incubator(
        ((ExtendedDoubleCounter) agentCounter).bind(attributes));
  }

  private ApplicationBoundDoubleCounter165Incubator(BoundDoubleCounter delegate) {
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
