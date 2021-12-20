/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.baseUnit;
import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.description;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.instrumentation.api.internal.GuardedBy;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;

final class AsyncInstrumentRegistry {

  private final Meter meter;

  @GuardedBy("gauges")
  private final Map<String, GaugeMeasurementsRecorder> gauges = new HashMap<>();

  AsyncInstrumentRegistry(Meter meter) {
    this.meter = meter;
  }

  <T> void buildGauge(
      io.micrometer.core.instrument.Meter.Id meterId,
      Attributes attributes,
      @Nullable T obj,
      ToDoubleFunction<T> objMetric) {

    synchronized (gauges) {
      GaugeMeasurementsRecorder recorder =
          gauges.computeIfAbsent(
              meterId.getName(),
              n -> {
                GaugeMeasurementsRecorder recorderCallback = new GaugeMeasurementsRecorder();
                meter
                    .gaugeBuilder(meterId.getName())
                    .setDescription(description(meterId))
                    .setUnit(baseUnit(meterId))
                    .buildWithCallback(recorderCallback);
                return recorderCallback;
              });
      recorder.addGaugeMeasurement(attributes, obj, objMetric);
    }
  }

  void removeGauge(String name, Attributes attributes) {
    synchronized (gauges) {
      GaugeMeasurementsRecorder recorder = gauges.get(name);
      if (recorder != null) {
        recorder.removeGaugeMeasurement(attributes);
        // if this was the last measurement then let's remove the whole recorder
        if (recorder.isEmpty()) {
          gauges.remove(name);
        }
      }
    }
  }

  private final class GaugeMeasurementsRecorder implements Consumer<ObservableDoubleMeasurement> {

    @GuardedBy("gauges")
    private final Map<Attributes, GaugeInfo> measurements = new HashMap<>();

    @Override
    public void accept(ObservableDoubleMeasurement measurement) {
      Map<Attributes, GaugeInfo> measurementsCopy;
      synchronized (gauges) {
        measurementsCopy = new HashMap<>(measurements);
      }

      measurementsCopy.forEach(
          (attributes, gauge) -> {
            Object obj = gauge.objWeakRef.get();
            if (obj != null) {
              measurement.record(gauge.metricFunction.applyAsDouble(obj), attributes);
            }
          });
    }

    <T> void addGaugeMeasurement(
        Attributes attributes, @Nullable T obj, ToDoubleFunction<T> objMetric) {
      synchronized (gauges) {
        measurements.put(attributes, new GaugeInfo(obj, (ToDoubleFunction<Object>) objMetric));
      }
    }

    void removeGaugeMeasurement(Attributes attributes) {
      synchronized (gauges) {
        measurements.remove(attributes);
      }
    }

    boolean isEmpty() {
      synchronized (gauges) {
        return measurements.isEmpty();
      }
    }
  }

  private static final class GaugeInfo {

    private final WeakReference<Object> objWeakRef;
    private final ToDoubleFunction<Object> metricFunction;

    private GaugeInfo(@Nullable Object obj, ToDoubleFunction<Object> metricFunction) {
      this.objWeakRef = new WeakReference<>(obj);
      this.metricFunction = metricFunction;
    }
  }
}
