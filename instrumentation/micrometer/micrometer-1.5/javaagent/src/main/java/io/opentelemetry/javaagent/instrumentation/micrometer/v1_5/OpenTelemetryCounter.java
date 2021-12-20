/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.baseUnit;
import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.description;
import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.toAttributes;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Measurement;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.Collections;

final class OpenTelemetryCounter implements Counter, RemovableMeter {

  private final Id id;
  // TODO: use bound instruments when they're available
  private final DoubleCounter otelCounter;
  private final Attributes attributes;

  private volatile boolean removed = false;

  OpenTelemetryCounter(Id id, Meter otelMeter) {
    this.id = id;
    this.otelCounter =
        otelMeter
            .counterBuilder(id.getName())
            .setDescription(description(id))
            .setUnit(baseUnit(id))
            .ofDoubles()
            .build();
    this.attributes = toAttributes(id.getTags());
  }

  @Override
  public void increment(double v) {
    if (removed) {
      return;
    }
    otelCounter.add(v, attributes);
  }

  @Override
  public double count() {
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
