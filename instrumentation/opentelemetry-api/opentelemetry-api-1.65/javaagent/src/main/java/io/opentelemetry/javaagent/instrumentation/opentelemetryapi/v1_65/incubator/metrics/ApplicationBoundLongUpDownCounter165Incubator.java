/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundLongUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundLongUpDownCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongUpDownCounter {

  private final BoundLongUpDownCounter delegate;

  static ApplicationBoundLongUpDownCounter165Incubator create(
      LongUpDownCounter agentCounter, Attributes attributes) {
    return new ApplicationBoundLongUpDownCounter165Incubator(
        ((ExtendedLongUpDownCounter) agentCounter).bind(attributes));
  }

  private ApplicationBoundLongUpDownCounter165Incubator(BoundLongUpDownCounter delegate) {
    this.delegate = delegate;
  }

  @Override
  public void add(long value) {
    delegate.add(value);
  }

  @Override
  public void add(long value, application.io.opentelemetry.context.Context applicationContext) {
    delegate.add(value, AgentContextStorage.getAgentContext(applicationContext));
  }
}
