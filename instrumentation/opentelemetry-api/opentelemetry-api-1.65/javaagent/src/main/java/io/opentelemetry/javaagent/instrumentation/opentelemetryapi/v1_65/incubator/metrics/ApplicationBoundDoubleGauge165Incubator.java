/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundDoubleGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleGauge;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundDoubleGauge165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleGauge {

  private final BoundDoubleGauge delegate;

  static ApplicationBoundDoubleGauge165Incubator create(
      DoubleGauge agentGauge, Attributes attributes) {
    return new ApplicationBoundDoubleGauge165Incubator(
        ((ExtendedDoubleGauge) agentGauge).bind(attributes));
  }

  private ApplicationBoundDoubleGauge165Incubator(BoundDoubleGauge delegate) {
    this.delegate = delegate;
  }

  @Override
  public void set(double value) {
    delegate.set(value);
  }

  @Override
  public void set(double value, application.io.opentelemetry.context.Context applicationContext) {
    delegate.set(value, AgentContextStorage.getAgentContext(applicationContext));
  }
}
