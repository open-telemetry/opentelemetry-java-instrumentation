/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundLongGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedLongGauge;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;

final class ApplicationBoundLongGauge165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongGauge {

  private final LongConsumer set;
  private final ObjLongConsumer<Context> setWithContext;

  static ApplicationBoundLongGauge165Incubator create(LongGauge agentGauge, Attributes attributes) {
    if (agentGauge instanceof ExtendedLongGauge) {
      BoundLongGauge boundGauge = ((ExtendedLongGauge) agentGauge).bind(attributes);
      return new ApplicationBoundLongGauge165Incubator(
          boundGauge::set, (context, value) -> boundGauge.set(value, context));
    }
    return new ApplicationBoundLongGauge165Incubator(
        value -> agentGauge.set(value, attributes),
        (context, value) -> agentGauge.set(value, attributes, context));
  }

  private ApplicationBoundLongGauge165Incubator(
      LongConsumer set, ObjLongConsumer<Context> setWithContext) {
    this.set = set;
    this.setWithContext = setWithContext;
  }

  @Override
  public void set(long value) {
    set.accept(value);
  }

  @Override
  public void set(long value, application.io.opentelemetry.context.Context applicationContext) {
    setWithContext.accept(AgentContextStorage.getAgentContext(applicationContext), value);
  }
}
