/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundDoubleHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;
import java.util.function.DoubleConsumer;
import java.util.function.ObjDoubleConsumer;

final class ApplicationBoundDoubleHistogram165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundDoubleHistogram {

  private final DoubleConsumer record;
  private final ObjDoubleConsumer<Context> recordWithContext;

  static ApplicationBoundDoubleHistogram165Incubator create(
      DoubleHistogram agentHistogram, Attributes attributes) {
    if (agentHistogram instanceof ExtendedDoubleHistogram) {
      BoundDoubleHistogram boundHistogram =
          ((ExtendedDoubleHistogram) agentHistogram).bind(attributes);
      return new ApplicationBoundDoubleHistogram165Incubator(
          boundHistogram::record, (context, value) -> boundHistogram.record(value, context));
    }
    return new ApplicationBoundDoubleHistogram165Incubator(
        value -> agentHistogram.record(value, attributes),
        (context, value) -> agentHistogram.record(value, attributes, context));
  }

  private ApplicationBoundDoubleHistogram165Incubator(
      DoubleConsumer record, ObjDoubleConsumer<Context> recordWithContext) {
    this.record = record;
    this.recordWithContext = recordWithContext;
  }

  @Override
  public void record(double value) {
    record.accept(value);
  }

  @Override
  public void record(
      double value, application.io.opentelemetry.context.Context applicationContext) {
    recordWithContext.accept(AgentContextStorage.getAgentContext(applicationContext), value);
  }
}
