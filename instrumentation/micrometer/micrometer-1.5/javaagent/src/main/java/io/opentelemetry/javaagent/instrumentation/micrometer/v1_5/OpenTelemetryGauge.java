/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.baseUnit;
import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.description;
import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.toAttributes;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import java.util.Collections;
import java.util.function.ToDoubleFunction;

final class OpenTelemetryGauge<T> implements Gauge, RemovableMeter {

  private final Id id;
  private final T obj;
  private final ToDoubleFunction<T> objMetric;
  private final Attributes attributes;

  volatile boolean removed = false;

  OpenTelemetryGauge(Id id, T obj, ToDoubleFunction<T> objMetric, Meter otelMeter) {
    this.id = id;
    this.obj = obj;
    this.objMetric = objMetric;
    this.attributes = toAttributes(id.getTags());

    otelMeter
        .gaugeBuilder(id.getName())
        .setDescription(description(id))
        .setUnit(baseUnit(id))
        .buildWithCallback(this::recordMeasurements);
  }

  private void recordMeasurements(ObservableDoubleMeasurement measurement) {
    if (removed) {
      return;
    }
    measurement.record(objMetric.applyAsDouble(obj), attributes);
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
    removed = true;
  }
}
