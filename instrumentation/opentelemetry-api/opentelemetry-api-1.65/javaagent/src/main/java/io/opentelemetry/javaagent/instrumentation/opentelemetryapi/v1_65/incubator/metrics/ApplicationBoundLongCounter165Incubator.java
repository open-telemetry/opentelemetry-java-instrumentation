/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundLongCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedLongCounter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundLongCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongCounter {

  private final BoundLongCounter delegate;

  static ApplicationBoundLongCounter165Incubator create(
      LongCounter agentCounter, Attributes attributes) {
    return new ApplicationBoundLongCounter165Incubator(
        ((ExtendedLongCounter) agentCounter).bind(attributes));
  }

  private ApplicationBoundLongCounter165Incubator(BoundLongCounter delegate) {
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
