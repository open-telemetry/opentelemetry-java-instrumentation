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
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.internal.GuardedBy;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import javax.annotation.Nullable;

final class AsyncInstrumentRegistry {

  private final Meter meter;

  @GuardedBy("gauges")
  private final Map<String, DoubleMeasurementsRecorder> gauges = new HashMap<>();

  @GuardedBy("doubleCounters")
  private final Map<String, DoubleMeasurementsRecorder> doubleCounters = new HashMap<>();

  @GuardedBy("longCounters")
  private final Map<String, LongMeasurementsRecorder> longCounters = new HashMap<>();

  AsyncInstrumentRegistry(Meter meter) {
    this.meter = meter;
  }

  <T> void buildGauge(
      io.micrometer.core.instrument.Meter.Id meterId,
      Attributes attributes,
      @Nullable T obj,
      ToDoubleFunction<T> objMetric) {

    buildGauge(
        meterId.getName(), description(meterId), baseUnit(meterId), attributes, obj, objMetric);
  }

  <T> void buildGauge(
      String name,
      String description,
      String baseUnit,
      Attributes attributes,
      @Nullable T obj,
      ToDoubleFunction<T> objMetric) {

    synchronized (gauges) {
      // use the gauges map as lock for the recorder state - this way all gauge-related mutable
      // state will always be accessed in synchronized(gauges)
      Object recorderLock = gauges;

      DoubleMeasurementsRecorder recorder =
          gauges.computeIfAbsent(
              name,
              n -> {
                DoubleMeasurementsRecorder recorderCallback =
                    new DoubleMeasurementsRecorder(recorderLock);
                meter
                    .gaugeBuilder(name)
                    .setDescription(description)
                    .setUnit(baseUnit)
                    .buildWithCallback(recorderCallback);
                return recorderCallback;
              });
      recorder.addMeasurement(
          attributes, new DoubleMetricInfo(obj, (ToDoubleFunction<Object>) objMetric));
    }
  }

  void removeGauge(String name, Attributes attributes) {
    synchronized (gauges) {
      removeMeasurement(gauges, name, attributes);
    }
  }

  <T> void buildDoubleCounter(
      io.micrometer.core.instrument.Meter.Id meterId,
      Attributes attributes,
      T obj,
      ToDoubleFunction<T> objMetric) {

    synchronized (doubleCounters) {
      // use the counters map as lock for the recorder state - this way all double counter-related
      // mutable state will always be accessed in synchronized(doubleCounters)
      Object recorderLock = doubleCounters;

      DoubleMeasurementsRecorder recorder =
          doubleCounters.computeIfAbsent(
              meterId.getName(),
              n -> {
                DoubleMeasurementsRecorder recorderCallback =
                    new DoubleMeasurementsRecorder(recorderLock);
                meter
                    .counterBuilder(meterId.getName())
                    .setDescription(description(meterId))
                    .setUnit(baseUnit(meterId))
                    .ofDoubles()
                    .buildWithCallback(recorderCallback);
                return recorderCallback;
              });
      recorder.addMeasurement(
          attributes, new DoubleMetricInfo(obj, (ToDoubleFunction<Object>) objMetric));
    }
  }

  void removeDoubleCounter(String name, Attributes attributes) {
    synchronized (doubleCounters) {
      removeMeasurement(doubleCounters, name, attributes);
    }
  }

  <T> void buildLongCounter(
      String name,
      String description,
      String baseUnit,
      Attributes attributes,
      @Nullable T obj,
      ToLongFunction<T> objMetric) {

    synchronized (longCounters) {
      // use the counters map as lock for the recorder state - this way all gauge-related mutable
      // state will always be accessed in synchronized(longCounters)
      Object recorderLock = longCounters;

      LongMeasurementsRecorder recorder =
          longCounters.computeIfAbsent(
              name,
              n -> {
                LongMeasurementsRecorder recorderCallback =
                    new LongMeasurementsRecorder(recorderLock);
                meter
                    .counterBuilder(name)
                    .setDescription(description)
                    .setUnit(baseUnit)
                    .buildWithCallback(recorderCallback);
                return recorderCallback;
              });
      recorder.addMeasurement(
          attributes, new LongMetricInfo(obj, (ToLongFunction<Object>) objMetric));
    }
  }

  void removeLongCounter(String name, Attributes attributes) {
    synchronized (longCounters) {
      removeMeasurement(longCounters, name, attributes);
    }
  }

  private static void removeMeasurement(
      Map<String, ? extends MutableMeasurementsRecorder<?>> registry,
      String name,
      Attributes attributes) {

    MutableMeasurementsRecorder<?> recorder = registry.get(name);
    if (recorder != null) {
      recorder.removeMeasurement(attributes);
      // if this was the last measurement then let's remove the whole recorder
      if (recorder.isEmpty()) {
        registry.remove(name);
      }
    }
  }

  private abstract static class MutableMeasurementsRecorder<I> {

    private final Object lock;

    @GuardedBy("lock")
    private final Map<Attributes, I> measurements = new HashMap<>();

    protected MutableMeasurementsRecorder(Object lock) {
      this.lock = lock;
    }

    Map<Attributes, I> copyForRead() {
      synchronized (lock) {
        return new HashMap<>(measurements);
      }
    }

    void addMeasurement(Attributes attributes, I info) {
      synchronized (lock) {
        measurements.put(attributes, info);
      }
    }

    void removeMeasurement(Attributes attributes) {
      synchronized (lock) {
        measurements.remove(attributes);
      }
    }

    boolean isEmpty() {
      synchronized (lock) {
        return measurements.isEmpty();
      }
    }
  }

  private static final class DoubleMeasurementsRecorder
      extends MutableMeasurementsRecorder<DoubleMetricInfo>
      implements Consumer<ObservableDoubleMeasurement> {

    private DoubleMeasurementsRecorder(Object lock) {
      super(lock);
    }

    @Override
    public void accept(ObservableDoubleMeasurement measurement) {
      copyForRead()
          .forEach(
              (attributes, gauge) -> {
                Object obj = gauge.objWeakRef.get();
                if (obj != null) {
                  measurement.record(gauge.metricFunction.applyAsDouble(obj), attributes);
                }
              });
    }
  }

  private static final class LongMeasurementsRecorder
      extends MutableMeasurementsRecorder<LongMetricInfo>
      implements Consumer<ObservableLongMeasurement> {

    private LongMeasurementsRecorder(Object lock) {
      super(lock);
    }

    @Override
    public void accept(ObservableLongMeasurement measurement) {
      copyForRead()
          .forEach(
              (attributes, gauge) -> {
                Object obj = gauge.objWeakRef.get();
                if (obj != null) {
                  measurement.record(gauge.metricFunction.applyAsLong(obj), attributes);
                }
              });
    }
  }

  private static final class DoubleMetricInfo {

    private final WeakReference<Object> objWeakRef;
    private final ToDoubleFunction<Object> metricFunction;

    private DoubleMetricInfo(@Nullable Object obj, ToDoubleFunction<Object> metricFunction) {
      this.objWeakRef = new WeakReference<>(obj);
      this.metricFunction = metricFunction;
    }
  }

  private static final class LongMetricInfo {

    private final WeakReference<Object> objWeakRef;
    private final ToLongFunction<Object> metricFunction;

    private LongMetricInfo(@Nullable Object obj, ToLongFunction<Object> metricFunction) {
      this.objWeakRef = new WeakReference<>(obj);
      this.metricFunction = metricFunction;
    }
  }
}
