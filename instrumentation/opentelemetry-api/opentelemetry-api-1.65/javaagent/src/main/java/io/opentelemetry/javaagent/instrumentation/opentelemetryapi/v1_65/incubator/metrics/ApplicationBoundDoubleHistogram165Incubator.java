/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundDoubleHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundDoubleHistogram165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleHistogram {

  private final BoundDoubleHistogram delegate;

  static ApplicationBoundDoubleHistogram165Incubator create(
      DoubleHistogram agentHistogram, Attributes attributes) {
    return new ApplicationBoundDoubleHistogram165Incubator(
        ((ExtendedDoubleHistogram) agentHistogram).bind(attributes));
  }

  private ApplicationBoundDoubleHistogram165Incubator(BoundDoubleHistogram delegate) {
    this.delegate = delegate;
  }

  @Override
  public void record(double value) {
    delegate.record(value);
  }

  @Override
  public void record(
      double value, application.io.opentelemetry.context.Context applicationContext) {
    delegate.record(value, AgentContextStorage.getAgentContext(applicationContext));
  }
}
