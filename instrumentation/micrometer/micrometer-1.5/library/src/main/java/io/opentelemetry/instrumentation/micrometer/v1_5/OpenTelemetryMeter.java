/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.baseUnit;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;
import static java.util.Collections.emptyList;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.instrumentation.api.incubator.metrics.internal.MetricCallbackRegistration;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.OpenTelemetryInstrument;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

final class OpenTelemetryMeter extends AbstractMeter
    implements Meter, RemovableMeter, OpenTelemetryInstrument {

  @Nullable private List<AutoCloseable> observableInstruments;

  @SuppressWarnings("UnusedVariable")
  @Nullable
  private List<Measurement> measurements;

  OpenTelemetryMeter(
      Id id,
      NamingConvention namingConvention,
      Iterable<Measurement> measurements,
      io.opentelemetry.api.metrics.Meter otelMeter,
      Bridging bridging) {
    super(id);
    Attributes attributes = tagsAsAttributes(id, namingConvention);

    List<AutoCloseable> observableInstruments = new ArrayList<>();
    List<Measurement> measurementReferences = new ArrayList<>();
    for (Measurement measurement : measurements) {
      measurementReferences.add(measurement);
      String name =
          bridging.statisticInstrumentName(id, measurement.getStatistic(), namingConvention);
      String description = bridging.description(name, id);
      String baseUnit = baseUnit(id);
      DoubleMeasurementRecorder<Measurement> callback =
          new DoubleMeasurementRecorder<>(measurement, Measurement::getValue, attributes);

      switch (measurement.getStatistic()) {
        case TOTAL:
        // fall through
        case TOTAL_TIME:
        case COUNT:
          DoubleCounterBuilder counterBuilder =
              otelMeter
                  .counterBuilder(name)
                  .ofDoubles()
                  .setDescription(description)
                  .setUnit(baseUnit);
          observableInstruments.add(
              MetricCallbackRegistration.withoutContextClassLoader(
                  () -> counterBuilder.buildWithCallback(callback)));
          break;

        case ACTIVE_TASKS:
          DoubleUpDownCounterBuilder upDownCounterBuilder =
              otelMeter
                  .upDownCounterBuilder(name)
                  .ofDoubles()
                  .setDescription(description)
                  .setUnit(baseUnit);
          observableInstruments.add(
              MetricCallbackRegistration.withoutContextClassLoader(
                  () -> upDownCounterBuilder.buildWithCallback(callback)));
          break;

        case DURATION:
        // fall through
        case MAX:
        case VALUE:
        case UNKNOWN:
          DoubleGaugeBuilder gaugeBuilder =
              otelMeter.gaugeBuilder(name).setDescription(description).setUnit(baseUnit);
          observableInstruments.add(
              MetricCallbackRegistration.withoutContextClassLoader(
                  () -> gaugeBuilder.buildWithCallback(callback)));
          break;
      }
    }
    this.observableInstruments = observableInstruments;
    this.measurements = measurementReferences;
  }

  @Override
  public Iterable<Measurement> measure() {
    UnsupportedReadLogger.logWarning();
    return emptyList();
  }

  @Override
  public void onRemove() {
    List<AutoCloseable> observableInstruments;
    try {
      synchronized (this) {
        observableInstruments = this.observableInstruments;
        this.observableInstruments = null;
        measurements = null;
      }
      if (observableInstruments == null) {
        return;
      }
      for (AutoCloseable observableInstrument : observableInstruments) {
        observableInstrument.close();
      }
    } catch (Exception e) {
      throw new IllegalStateException("SDK instruments should never throw on close()", e);
    }
  }
}
