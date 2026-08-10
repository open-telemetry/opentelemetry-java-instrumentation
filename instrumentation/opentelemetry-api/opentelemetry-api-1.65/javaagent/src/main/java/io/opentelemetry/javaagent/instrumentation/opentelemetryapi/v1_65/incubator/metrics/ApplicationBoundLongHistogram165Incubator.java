/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_65.incubator.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundLongHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogram;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_0.context.AgentContextStorage;
import java.util.function.LongConsumer;
import java.util.function.ObjLongConsumer;

final class ApplicationBoundLongHistogram165Incubator
    implements application.io.opentelemetry.api.incubator.metrics.BoundLongHistogram {

  private final LongConsumer record;
  private final ObjLongConsumer<Context> recordWithContext;

  static ApplicationBoundLongHistogram165Incubator create(
      LongHistogram agentHistogram, Attributes attributes) {
    if (agentHistogram instanceof ExtendedLongHistogram) {
      BoundLongHistogram boundHistogram = ((ExtendedLongHistogram) agentHistogram).bind(attributes);
      return new ApplicationBoundLongHistogram165Incubator(
          boundHistogram::record, (context, value) -> boundHistogram.record(value, context));
    }
    return new ApplicationBoundLongHistogram165Incubator(
        value -> agentHistogram.record(value, attributes),
        (context, value) -> agentHistogram.record(value, attributes, context));
  }

  private ApplicationBoundLongHistogram165Incubator(
      LongConsumer record, ObjLongConsumer<Context> recordWithContext) {
    this.record = record;
    this.recordWithContext = recordWithContext;
  }

  @Override
  public void record(long value) {
    record.accept(value);
  }

  @Override
  public void record(long value, application.io.opentelemetry.context.Context applicationContext) {
    recordWithContext.accept(AgentContextStorage.getAgentContext(applicationContext), value);
  }
}
