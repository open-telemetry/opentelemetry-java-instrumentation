/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundDoubleGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleGauge;
import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;
import java.util.function.DoubleConsumer;
import java.util.function.ObjDoubleConsumer;

final class ApplicationBoundDoubleGauge165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleGauge {

  private final DoubleConsumer set;
  private final ObjDoubleConsumer<Context> setWithContext;

  static ApplicationBoundDoubleGauge165Incubator create(
      DoubleGauge agentGauge, Attributes attributes) {
    if (agentGauge instanceof ExtendedDoubleGauge) {
      BoundDoubleGauge boundGauge = ((ExtendedDoubleGauge) agentGauge).bind(attributes);
      return new ApplicationBoundDoubleGauge165Incubator(
          boundGauge::set, (context, value) -> boundGauge.set(value, context));
    }
    return new ApplicationBoundDoubleGauge165Incubator(
        value -> agentGauge.set(value, attributes),
        (context, value) -> agentGauge.set(value, attributes, context));
  }

  private ApplicationBoundDoubleGauge165Incubator(
      DoubleConsumer set, ObjDoubleConsumer<Context> setWithContext) {
    this.set = set;
    this.setWithContext = setWithContext;
  }

  @Override
  public void set(double value) {
    set.accept(value);
  }

  @Override
  public void set(double value, application.io.opentelemetry.context.Context applicationContext) {
    setWithContext.accept(AgentContextStorage.getAgentContext(applicationContext), value);
  }
}
