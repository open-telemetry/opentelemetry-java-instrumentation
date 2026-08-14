/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundLongHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;

final class ApplicationBoundLongHistogram165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongHistogram {

  private final BoundLongHistogram delegate;

  static ApplicationBoundLongHistogram165Incubator create(
      LongHistogram agentHistogram, Attributes attributes) {
    return new ApplicationBoundLongHistogram165Incubator(
        ((ExtendedLongHistogram) agentHistogram).bind(attributes));
  }

  private ApplicationBoundLongHistogram165Incubator(BoundLongHistogram delegate) {
    this.delegate = delegate;
  }

  @Override
  public void record(long value) {
    delegate.record(value);
  }

  @Override
  public void record(long value, application.io.opentelemetry.context.Context applicationContext) {
    delegate.record(value, AgentContextStorage.getAgentContext(applicationContext));
  }
}
