/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.toAttributes;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.opentelemetry.api.common.Attributes;
import java.util.Collections;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;

final class OpenTelemetryGauge<T> implements Gauge, RemovableMeter {

  private final Id id;
  private final Attributes attributes;
  private final AsyncInstrumentRegistry asyncInstrumentRegistry;

  OpenTelemetryGauge(
      Id id,
      @Nullable T obj,
      ToDoubleFunction<T> objMetric,
      AsyncInstrumentRegistry asyncInstrumentRegistry) {
    this.id = id;
    this.attributes = toAttributes(id.getTags());
    this.asyncInstrumentRegistry = asyncInstrumentRegistry;

    asyncInstrumentRegistry.buildGauge(id, attributes, obj, objMetric);
  }

  @Override
  public double value() {
    // OpenTelemetry metrics bridge does not support reading measurements
    return Double.NaN;
  }

  @Override
  public Iterable<Measurement> measure() {
    // OpenTelemetry metrics bridge does not support reading measurements
    return Collections.emptyList();
  }

  @Override
  public Id getId() {
    return id;
  }

  @Override
  public void onRemove() {
    asyncInstrumentRegistry.removeGauge(id.getName(), attributes);
  }
}
