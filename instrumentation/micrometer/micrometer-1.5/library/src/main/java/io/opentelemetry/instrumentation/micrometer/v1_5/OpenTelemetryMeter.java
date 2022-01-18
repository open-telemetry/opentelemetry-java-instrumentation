/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.baseUnit;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.description;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.statisticInstrumentName;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;

import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.micrometer.v1_5.AsyncInstrumentRegistry.AsyncMeasurementHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

@SuppressWarnings("HashCodeToString")
final class OpenTelemetryMeter implements Meter, RemovableMeter {

  private final Id id;
  private final List<AsyncMeasurementHandle> measurementHandles;

  OpenTelemetryMeter(
      Id id, Iterable<Measurement> measurements, AsyncInstrumentRegistry asyncInstrumentRegistry) {
    this.id = id;
    Attributes attributes = tagsAsAttributes(id);

    List<AsyncMeasurementHandle> measurementHandles = new ArrayList<>();
    for (Measurement measurement : measurements) {
      String name = statisticInstrumentName(id, measurement.getStatistic());
      String description = description(id);
      String baseUnit = baseUnit(id);

      switch (measurement.getStatistic()) {
        case TOTAL:
          // fall through
        case TOTAL_TIME:
        case COUNT:
          measurementHandles.add(
              asyncInstrumentRegistry.buildDoubleCounter(
                  name, description, baseUnit, attributes, measurement, Measurement::getValue));
          break;

        case ACTIVE_TASKS:
          measurementHandles.add(
              asyncInstrumentRegistry.buildUpDownDoubleCounter(
                  name, description, baseUnit, attributes, measurement, Measurement::getValue));
          break;

        case DURATION:
          // fall through
        case MAX:
        case VALUE:
        case UNKNOWN:
          measurementHandles.add(
              asyncInstrumentRegistry.buildGauge(
                  name, description, baseUnit, attributes, measurement, Measurement::getValue));
          break;
      }
    }
    this.measurementHandles = measurementHandles;
  }

  @Override
  public Id getId() {
    return id;
  }

  @Override
  public Iterable<Measurement> measure() {
    UnsupportedReadLogger.logWarning();
    return Collections.emptyList();
  }

  @Override
  public void onRemove() {
    measurementHandles.forEach(AsyncMeasurementHandle::remove);
  }

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(@Nullable Object o) {
    return MeterEquivalence.equals(this, o);
  }

  @Override
  public int hashCode() {
    return MeterEquivalence.hashCode(this);
  }
}
