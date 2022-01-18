/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.cache.Cache;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import javax.annotation.Nullable;

// TODO: refactor this class, there's too much copy-paste here
public final class AsyncInstrumentRegistry {

  // we need to re-use instrument registries per OpenTelemetry instance so that async instruments
  // that were created by other OpenTelemetryMeterRegistries can be reused; otherwise the SDK will
  // start logging errors and async measurements will not be recorded
  private static final Cache<Meter, AsyncInstrumentRegistry> asyncInstrumentRegistries =
      Cache.weak();

  /**
   * Returns the {@link AsyncInstrumentRegistry} for the passed {@link Meter}. There is at most one
   * {@link AsyncInstrumentRegistry} created for each OpenTelemetry {@link Meter}.
   */
  public static AsyncInstrumentRegistry getOrCreate(Meter meter) {
    return asyncInstrumentRegistries.computeIfAbsent(meter, AsyncInstrumentRegistry::new);
  }

  // using a weak ref so that the AsyncInstrumentRegistry (which is stored in a static maps) does
  // not hold strong references to Meter (and thus make it impossible to collect Meter garbage).
  // in practice this should never return null - OpenTelemetryMeterRegistry maintains a strong
  // reference to both Meter and AsyncInstrumentRegistry; if the meter registry is GC'd then its
  // corresponding AsyncInstrumentRegistry cannot possibly be used; and Meter cannot be GC'd until
  // OpenTelemetryMeterRegistry is GC'd
  private final WeakReference<Meter> meter;

  // values from the maps below are never removed - that is because the underlying OpenTelemetry
  // async instruments are never removed; if we removed the recorder and tried to register it once
  // again OTel would log an error and basically ignore the new callback
  // these maps are GC'd together with this AsyncInstrumentRegistry instance - that is, when the
  // whole OpenTelemetry Meter gets GC'd

  private final Map<String, DoubleMeasurementsRecorder> gauges = new ConcurrentHashMap<>();
  private final Map<String, DoubleMeasurementsRecorder> doubleCounters = new ConcurrentHashMap<>();
  private final Map<String, LongMeasurementsRecorder> longCounters = new ConcurrentHashMap<>();
  private final Map<String, DoubleMeasurementsRecorder> upDownDoubleCounters =
      new ConcurrentHashMap<>();

  AsyncInstrumentRegistry(Meter meter) {
    this.meter = new WeakReference<>(meter);
  }

  public <T> AsyncMeasurementHandle buildGauge(
      String name,
      String description,
      String baseUnit,
      Attributes attributes,
      @Nullable T obj,
      ToDoubleFunction<T> objMetric) {

    DoubleMeasurementsRecorder recorder =
        gauges.computeIfAbsent(
            name,
            n -> {
              DoubleMeasurementsRecorder recorderCallback = new DoubleMeasurementsRecorder();
              otelMeter()
                  .gaugeBuilder(name)
                  .setDescription(description)
                  .setUnit(baseUnit)
                  .buildWithCallback(recorderCallback);
              return recorderCallback;
            });
    recorder.addMeasurement(attributes, new DoubleMeasurementSource<>(obj, objMetric));

    return new AsyncMeasurementHandle(recorder, attributes);
  }

  public <T> AsyncMeasurementHandle buildDoubleCounter(
      String name,
      String description,
      String baseUnit,
      Attributes attributes,
      @Nullable T obj,
      ToDoubleFunction<T> objMetric) {

    DoubleMeasurementsRecorder recorder =
        doubleCounters.computeIfAbsent(
            name,
            n -> {
              DoubleMeasurementsRecorder recorderCallback = new DoubleMeasurementsRecorder();
              otelMeter()
                  .counterBuilder(name)
                  .setDescription(description)
                  .setUnit(baseUnit)
                  .ofDoubles()
                  .buildWithCallback(recorderCallback);
              return recorderCallback;
            });
    recorder.addMeasurement(attributes, new DoubleMeasurementSource<>(obj, objMetric));

    return new AsyncMeasurementHandle(recorder, attributes);
  }

  public <T> AsyncMeasurementHandle buildLongCounter(
      String name,
      String description,
      String baseUnit,
      Attributes attributes,
      @Nullable T obj,
      ToLongFunction<T> objMetric) {

    LongMeasurementsRecorder recorder =
        longCounters.computeIfAbsent(
            name,
            n -> {
              LongMeasurementsRecorder recorderCallback = new LongMeasurementsRecorder();
              otelMeter()
                  .counterBuilder(name)
                  .setDescription(description)
                  .setUnit(baseUnit)
                  .buildWithCallback(recorderCallback);
              return recorderCallback;
            });
    recorder.addMeasurement(attributes, new LongMeasurementSource<>(obj, objMetric));

    return new AsyncMeasurementHandle(recorder, attributes);
  }

  public <T> AsyncMeasurementHandle buildUpDownDoubleCounter(
      String name,
      String description,
      String baseUnit,
      Attributes attributes,
      T obj,
      ToDoubleFunction<T> objMetric) {

    DoubleMeasurementsRecorder recorder =
        upDownDoubleCounters.computeIfAbsent(
            name,
            n -> {
              DoubleMeasurementsRecorder recorderCallback = new DoubleMeasurementsRecorder();
              otelMeter()
                  .upDownCounterBuilder(name)
                  .setDescription(description)
                  .setUnit(baseUnit)
                  .ofDoubles()
                  .buildWithCallback(recorderCallback);
              return recorderCallback;
            });
    recorder.addMeasurement(attributes, new DoubleMeasurementSource<>(obj, objMetric));

    return new AsyncMeasurementHandle(recorder, attributes);
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

    final Map<Attributes, I> measurements = new ConcurrentHashMap<>();

    void addMeasurement(Attributes attributes, I info) {
      measurements.put(attributes, info);
    }

    void removeMeasurement(Attributes attributes) {
      measurements.remove(attributes);
    }
  }

  private static final class DoubleMeasurementsRecorder
      extends MeasurementsRecorder<DoubleMeasurementSource<?>>
      implements Consumer<ObservableDoubleMeasurement> {

    @Override
    public void accept(ObservableDoubleMeasurement measurement) {
      measurements.forEach((attributes, gauge) -> record(measurement, attributes, gauge));
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

    @Override
    public void accept(ObservableLongMeasurement measurement) {
      measurements.forEach((attributes, gauge) -> record(measurement, attributes, gauge));
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

  public static final class AsyncMeasurementHandle {

    private final MeasurementsRecorder<?> measurementsRecorder;
    private final Attributes attributes;

    AsyncMeasurementHandle(MeasurementsRecorder<?> measurementsRecorder, Attributes attributes) {
      this.measurementsRecorder = measurementsRecorder;
      this.attributes = attributes;
    }

    public void remove() {
      measurementsRecorder.removeMeasurement(attributes);
    }
  }
}
