/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundDoubleUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounter;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;
import java.util.function.DoubleConsumer;
import java.util.function.ObjDoubleConsumer;

final class ApplicationBoundDoubleUpDownCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleUpDownCounter {

  private final DoubleConsumer add;
  private final ObjDoubleConsumer<Context> addWithContext;

  static ApplicationBoundDoubleUpDownCounter165Incubator create(
      DoubleUpDownCounter agentCounter, Attributes attributes) {
    if (agentCounter instanceof ExtendedDoubleUpDownCounter) {
      BoundDoubleUpDownCounter boundCounter =
          ((ExtendedDoubleUpDownCounter) agentCounter).bind(attributes);
      return new ApplicationBoundDoubleUpDownCounter165Incubator(
          boundCounter::add, (context, value) -> boundCounter.add(value, context));
    }
    return new ApplicationBoundDoubleUpDownCounter165Incubator(
        value -> agentCounter.add(value, attributes),
        (context, value) -> agentCounter.add(value, attributes, context));
  }

  private ApplicationBoundDoubleUpDownCounter165Incubator(
      DoubleConsumer add, ObjDoubleConsumer<Context> addWithContext) {
    this.add = add;
    this.addWithContext = addWithContext;
  }

  @Override
  public void add(double value) {
    add.accept(value);
  }

  @Override
  public void add(double value, application.io.opentelemetry.context.Context applicationContext) {
    addWithContext.accept(AgentContextStorage.getAgentContext(applicationContext), value);
  }
}
