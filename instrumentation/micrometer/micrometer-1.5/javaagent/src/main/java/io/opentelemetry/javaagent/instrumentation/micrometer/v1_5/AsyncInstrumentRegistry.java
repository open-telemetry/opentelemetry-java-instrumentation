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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
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

    GaugeMeasurementsRecorder recorder;
    synchronized (gauges) {
      recorder =
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
    }

    // kept outside the synchronized block to avoid holding two locks at once
    recorder.addGaugeMeasurement(attributes, obj, objMetric);
  }

  void removeGauge(String name, Attributes attributes) {
    GaugeMeasurementsRecorder recorder;
    synchronized (gauges) {
      recorder = gauges.get(name);
      // if this is the last measurement then let's remove the whole recorder
      if (recorder != null && recorder.size() == 1) {
        gauges.remove(name);
      }
    }

    // kept outside the synchronized block to avoid holding two locks at once
    if (recorder != null) {
      recorder.removeGaugeMeasurement(attributes);
    }
  }

  @ThreadSafe
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

    <T> void addGaugeMeasurement(
        Attributes attributes, @Nullable T obj, ToDoubleFunction<T> objMetric) {
      measurements.put(attributes, new GaugeInfo(obj, (ToDoubleFunction<Object>) objMetric));
    }

    void removeGaugeMeasurement(Attributes attributes) {
      measurements.remove(attributes);
    }

    int size() {
      return measurements.size();
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
