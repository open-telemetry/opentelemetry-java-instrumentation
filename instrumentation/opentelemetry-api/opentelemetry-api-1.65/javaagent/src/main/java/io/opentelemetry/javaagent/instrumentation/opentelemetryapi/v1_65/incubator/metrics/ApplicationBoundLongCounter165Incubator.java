/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundLongCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedLongCounter;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;

final class ApplicationBoundLongCounter165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongCounter {

  private final LongConsumer add;
  private final ObjLongConsumer<Context> addWithContext;

  static ApplicationBoundLongCounter165Incubator create(
      LongCounter agentCounter, Attributes attributes) {
    if (agentCounter instanceof ExtendedLongCounter) {
      BoundLongCounter boundCounter = ((ExtendedLongCounter) agentCounter).bind(attributes);
      return new ApplicationBoundLongCounter165Incubator(
          boundCounter::add, (context, value) -> boundCounter.add(value, context));
    }
    return new ApplicationBoundLongCounter165Incubator(
        value -> agentCounter.add(value, attributes),
        (context, value) -> agentCounter.add(value, attributes, context));
  }

  private ApplicationBoundLongCounter165Incubator(
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
