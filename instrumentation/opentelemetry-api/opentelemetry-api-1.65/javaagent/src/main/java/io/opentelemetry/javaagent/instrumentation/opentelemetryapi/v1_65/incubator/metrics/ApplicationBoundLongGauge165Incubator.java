/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundLongGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedLongGauge;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundLongGauge165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongGauge {

  private final BoundLongGauge delegate;

  static ApplicationBoundLongGauge165Incubator create(LongGauge agentGauge, Attributes attributes) {
    return new ApplicationBoundLongGauge165Incubator(
        ((ExtendedLongGauge) agentGauge).bind(attributes));
  }

  private ApplicationBoundLongGauge165Incubator(BoundLongGauge delegate) {
    this.delegate = delegate;
  }

  @Override
  public void set(long value) {
    delegate.set(value);
  }

  @Override
  public void set(long value, application.io.opentelemetry.context.Context applicationContext) {
    delegate.set(value, AgentContextStorage.getAgentContext(applicationContext));
  }
}
