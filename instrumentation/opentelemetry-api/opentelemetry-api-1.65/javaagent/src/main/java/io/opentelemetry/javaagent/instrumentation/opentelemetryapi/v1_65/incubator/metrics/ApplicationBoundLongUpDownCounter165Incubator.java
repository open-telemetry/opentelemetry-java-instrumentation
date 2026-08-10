/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundLongUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounter;
import io.opentelemetry.api.metrics.LongUpDownCounter;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;

final class ApplicationBoundLongUpDownCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongUpDownCounter {

  private final LongConsumer add;
  private final ObjLongConsumer<Context> addWithContext;

  static ApplicationBoundLongUpDownCounter165Incubator create(
      LongUpDownCounter agentCounter, Attributes attributes) {
    if (agentCounter instanceof ExtendedLongUpDownCounter) {
      BoundLongUpDownCounter boundCounter =
          ((ExtendedLongUpDownCounter) agentCounter).bind(attributes);
      return new ApplicationBoundLongUpDownCounter165Incubator(
          boundCounter::add, (context, value) -> boundCounter.add(value, context));
    }
    return new ApplicationBoundLongUpDownCounter165Incubator(
        value -> agentCounter.add(value, attributes),
        (context, value) -> agentCounter.add(value, attributes, context));
  }

  private ApplicationBoundLongUpDownCounter165Incubator(
      LongConsumer add, ObjLongConsumer<Context> addWithContext) {
    this.add = add;
    this.addWithContext = addWithContext;
  }

  @Override
  public void add(long value) {
    add.accept(value);
  }

  @Override
  public void add(long value, application.io.opentelemetry.context.Context applicationContext) {
    addWithContext.accept(AgentContextStorage.getAgentContext(applicationContext), value);
  }
}
