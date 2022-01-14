/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.baseUnit;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.description;

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

// TODO: refactor this class, there's too much copy-paste here
final class AsyncInstrumentRegistry {

  // using a weak ref so that the AsyncInstrumentRegistry (which is stored in a static maps) does
  // not hold strong references to Meter (and thus make it impossible to collect Meter garbage).
  // in practice this should never return null - OpenTelemetryMeterRegistry maintains a strong
  // reference to both Meter and AsyncInstrumentRegistry; if the meter registry is GC'd then its
  // corresponding AsyncInstrumentRegistry cannot possibly be used; and Meter cannot be GC'd until
  // OpentelemetryMeterRegistry is GC'd
  private final WeakReference<Meter> meter;

  // we're always locking lock on the whole instrument map; the add/remove methods aren't called
  // that often, so it's probably better to opt for correctness in that case - there is a small
  // window between removing a single measurement and removing the whole instrument (if it has no
  // more measurements) when potentially a new measurement could be added; a ConcurrentHashMap
  // wouldn't be enough in this case

  @GuardedBy("gauges")
  private final Map<String, DoubleMeasurementsRecorder> gauges = new HashMap<>();

  @GuardedBy("doubleCounters")
  private final Map<String, DoubleMeasurementsRecorder> doubleCounters = new HashMap<>();

  @GuardedBy("longCounters")
  private final Map<String, LongMeasurementsRecorder> longCounters = new HashMap<>();

  @GuardedBy("upDownDoubleCounters")
  private final Map<String, DoubleMeasurementsRecorder> upDownDoubleCounters = new HashMap<>();

  AsyncInstrumentRegistry(Meter meter) {
    this.meter = new WeakReference<>(meter);
  }

  <T> AsyncMeasurementHandle buildGauge(
      io.micrometer.core.instrument.Meter.Id meterId,
      Attributes attributes,
      @Nullable T obj,
      ToDoubleFunction<T> objMetric) {
    return buildGauge(
        meterId.getName(), description(meterId), baseUnit(meterId), attributes, obj, objMetric);
  }

  <T> AsyncMeasurementHandle buildGauge(
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
                otelMeter()
                    .gaugeBuilder(name)
                    .setDescription(description)
                    .setUnit(baseUnit)
                    .buildWithCallback(recorderCallback);
                return recorderCallback;
              });
      recorder.addMeasurement(attributes, new DoubleMeasurementSource<>(obj, objMetric));

      return new AsyncMeasurementHandle(gauges, name, attributes);
    }
  }

  <T> AsyncMeasurementHandle buildDoubleCounter(
      io.micrometer.core.instrument.Meter.Id meterId,
      Attributes attributes,
      T obj,
      ToDoubleFunction<T> objMetric) {
    return buildDoubleCounter(
        meterId.getName(), description(meterId), baseUnit(meterId), attributes, obj, objMetric);
  }

  <T> AsyncMeasurementHandle buildDoubleCounter(
      String name,
      String description,
      String baseUnit,
      Attributes attributes,
      @Nullable T obj,
      ToDoubleFunction<T> objMetric) {

    synchronized (doubleCounters) {
      // use the counters map as lock for the recorder state - this way all double counter-related
      // mutable state will always be accessed in synchronized(doubleCounters)
      Object recorderLock = doubleCounters;

      DoubleMeasurementsRecorder recorder =
          doubleCounters.computeIfAbsent(
              name,
              n -> {
                DoubleMeasurementsRecorder recorderCallback =
                    new DoubleMeasurementsRecorder(recorderLock);
                otelMeter()
                    .counterBuilder(name)
                    .setDescription(description)
                    .setUnit(baseUnit)
                    .ofDoubles()
                    .buildWithCallback(recorderCallback);
                return recorderCallback;
              });
      recorder.addMeasurement(attributes, new DoubleMeasurementSource<>(obj, objMetric));

      return new AsyncMeasurementHandle(doubleCounters, name, attributes);
    }
  }

  <T> AsyncMeasurementHandle buildLongCounter(
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
                otelMeter()
                    .counterBuilder(name)
                    .setDescription(description)
                    .setUnit(baseUnit)
                    .buildWithCallback(recorderCallback);
                return recorderCallback;
              });
      recorder.addMeasurement(attributes, new LongMeasurementSource<>(obj, objMetric));

      return new AsyncMeasurementHandle(longCounters, name, attributes);
    }
  }

  <T> AsyncMeasurementHandle buildUpDownDoubleCounter(
      String name,
      String description,
      String baseUnit,
      Attributes attributes,
      T obj,
      ToDoubleFunction<T> objMetric) {

    synchronized (upDownDoubleCounters) {
      // use the counters map as lock for the recorder state - this way all double counter-related
      // mutable state will always be accessed in synchronized(upDownDoubleCounters)
      Object recorderLock = upDownDoubleCounters;

      DoubleMeasurementsRecorder recorder =
          upDownDoubleCounters.computeIfAbsent(
              name,
              n -> {
                DoubleMeasurementsRecorder recorderCallback =
                    new DoubleMeasurementsRecorder(recorderLock);
                otelMeter()
                    .upDownCounterBuilder(name)
                    .setDescription(description)
                    .setUnit(baseUnit)
                    .ofDoubles()
                    .buildWithCallback(recorderCallback);
                return recorderCallback;
              });
      recorder.addMeasurement(attributes, new DoubleMeasurementSource<>(obj, objMetric));

      return new AsyncMeasurementHandle(upDownDoubleCounters, name, attributes);
    }
  }

  private Meter otelMeter() {
    Meter otelMeter = meter.get();
    if (otelMeter == null) {
      throw new IllegalStateException(
          "OpenTelemetry Meter was garbage-collected, but the async instrument registry was not");
    }
    return otelMeter;
  }

  private abstract static class MeasurementsRecorder<I> {

    private final Object lock;

    @GuardedBy("lock")
    private final Map<Attributes, I> measurements = new HashMap<>();

    protected MeasurementsRecorder(Object lock) {
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
      extends MeasurementsRecorder<DoubleMeasurementSource<?>>
      implements Consumer<ObservableDoubleMeasurement> {

    private DoubleMeasurementsRecorder(Object lock) {
      super(lock);
    }

    @Override
    public void accept(ObservableDoubleMeasurement measurement) {
      copyForRead().forEach((attributes, gauge) -> record(measurement, attributes, gauge));
    }

    private static <T> void record(
        ObservableDoubleMeasurement measurement,
        Attributes attributes,
        DoubleMeasurementSource<T> gauge) {
      T obj = gauge.objWeakRef.get();
      if (obj != null) {
        measurement.record(gauge.metricFunction.applyAsDouble(obj), attributes);
      }
    }
  }

  private static final class LongMeasurementsRecorder
      extends MeasurementsRecorder<LongMeasurementSource<?>>
      implements Consumer<ObservableLongMeasurement> {

    private LongMeasurementsRecorder(Object lock) {
      super(lock);
    }

    @Override
    public void accept(ObservableLongMeasurement measurement) {
      copyForRead().forEach((attributes, gauge) -> record(measurement, attributes, gauge));
    }

    private static <T> void record(
        ObservableLongMeasurement measurement,
        Attributes attributes,
        LongMeasurementSource<T> gauge) {
      T obj = gauge.objWeakRef.get();
      if (obj != null) {
        measurement.record(gauge.metricFunction.applyAsLong(obj), attributes);
      }
    }
  }

  private static final class DoubleMeasurementSource<T> {

    private final WeakReference<T> objWeakRef;
    private final ToDoubleFunction<T> metricFunction;

    private DoubleMeasurementSource(@Nullable T obj, ToDoubleFunction<T> metricFunction) {
      this.objWeakRef = new WeakReference<>(obj);
      this.metricFunction = metricFunction;
    }
  }

  private static final class LongMeasurementSource<T> {

    private final WeakReference<T> objWeakRef;
    private final ToLongFunction<T> metricFunction;

    private LongMeasurementSource(@Nullable T obj, ToLongFunction<T> metricFunction) {
      this.objWeakRef = new WeakReference<>(obj);
      this.metricFunction = metricFunction;
    }
  }

  static final class AsyncMeasurementHandle {

    @GuardedBy("instrumentRegistry")
    private final Map<String, ? extends MeasurementsRecorder<?>> instrumentRegistry;

    private final String name;
    private final Attributes attributes;

    AsyncMeasurementHandle(
        Map<String, ? extends MeasurementsRecorder<?>> instrumentRegistry,
        String name,
        Attributes attributes) {
      this.instrumentRegistry = instrumentRegistry;
      this.name = name;
      this.attributes = attributes;
    }

    void remove() {
      synchronized (instrumentRegistry) {
        MeasurementsRecorder<?> recorder = instrumentRegistry.get(name);
        if (recorder != null) {
          recorder.removeMeasurement(attributes);
          // if this was the last measurement then let's remove the whole recorder
          if (recorder.isEmpty()) {
            instrumentRegistry.remove(name);
          }
        }
      }
    }
  }
}
