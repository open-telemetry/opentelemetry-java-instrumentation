/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;

final class AsyncInstrumentRegistry {

  private final Meter meter;
  private final ConcurrentMap<String, GaugeMeasurementsRecorder> gauges = new ConcurrentHashMap<>();

  AsyncInstrumentRegistry(Meter meter) {
    this.meter = meter;
  }

  <T> void buildGauge(
      String name,
      String description,
      String baseUnit,
      Attributes attributes,
      T obj,
      ToDoubleFunction<T> objMetric) {
    gauges
        .computeIfAbsent(
            name,
            n -> {
              GaugeMeasurementsRecorder recorder = new GaugeMeasurementsRecorder();
              meter
                  .gaugeBuilder(name)
                  .setDescription(description)
                  .setUnit(baseUnit)
                  .buildWithCallback(recorder);
              return recorder;
            })
        .addGaugeMeasurement(attributes, obj, objMetric);
  }

  void removeGauge(String name, Attributes attributes) {
    GaugeMeasurementsRecorder recorder = gauges.get(name);
    if (recorder != null) {
      recorder.removeGaugeMeasurement(attributes);
    }
  }

  private static final class GaugeMeasurementsRecorder
      implements Consumer<ObservableDoubleMeasurement> {

    private final ConcurrentMap<Attributes, GaugeInfo> measurements = new ConcurrentHashMap<>();

    @Override
    public void accept(ObservableDoubleMeasurement measurement) {
      measurements.forEach(
          (attributes, gauge) -> {
            Object obj = gauge.objWeakRef.get();
            if (obj != null) {
              measurement.record(gauge.metricFunction.applyAsDouble(obj), attributes);
            }
          });
    }

    <T> void addGaugeMeasurement(Attributes attributes, T obj, ToDoubleFunction<T> objMetric) {
      measurements.put(attributes, new GaugeInfo(obj, (ToDoubleFunction<Object>) objMetric));
    }

    void removeGaugeMeasurement(Attributes attributes) {
      measurements.remove(attributes);
    }
  }

  private static final class GaugeInfo {

    private final WeakReference<Object> objWeakRef;
    private final ToDoubleFunction<Object> metricFunction;

    private GaugeInfo(Object obj, ToDoubleFunction<Object> metricFunction) {
      this.objWeakRef = new WeakReference<>(obj);
      this.metricFunction = metricFunction;
    }
  }
}
