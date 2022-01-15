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
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    recorder.addMeasurement(
        attributes, new DoubleMeasurementSource(obj, (ToDoubleFunction<Object>) objMetric));

    return new AsyncMeasurementHandle(recorder, attributes);
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
    recorder.addMeasurement(
        attributes, new DoubleMeasurementSource(obj, (ToDoubleFunction<Object>) objMetric));

    return new AsyncMeasurementHandle(recorder, attributes);
  }

  <T> AsyncMeasurementHandle buildLongCounter(
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
    recorder.addMeasurement(
        attributes, new LongMeasurementSource(obj, (ToLongFunction<Object>) objMetric));

    return new AsyncMeasurementHandle(recorder, attributes);
  }

  <T> AsyncMeasurementHandle buildUpDownDoubleCounter(
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
    recorder.addMeasurement(
        attributes, new DoubleMeasurementSource(obj, (ToDoubleFunction<Object>) objMetric));

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
      extends MeasurementsRecorder<DoubleMeasurementSource>
      implements Consumer<ObservableDoubleMeasurement> {

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
  }

  private static final class LongMeasurementsRecorder
      extends MeasurementsRecorder<LongMeasurementSource>
      implements Consumer<ObservableLongMeasurement> {

    @Override
    public void accept(ObservableLongMeasurement measurement) {
      measurements.forEach(
          (attributes, gauge) -> {
            Object obj = gauge.objWeakRef.get();
            if (obj != null) {
              measurement.record(gauge.metricFunction.applyAsLong(obj), attributes);
            }
          });
    }
  }

  private static final class DoubleMeasurementSource {

    private final WeakReference<Object> objWeakRef;
    private final ToDoubleFunction<Object> metricFunction;

    private DoubleMeasurementSource(@Nullable Object obj, ToDoubleFunction<Object> metricFunction) {
      this.objWeakRef = new WeakReference<>(obj);
      this.metricFunction = metricFunction;
    }
  }

  private static final class LongMeasurementSource {

    private final WeakReference<Object> objWeakRef;
    private final ToLongFunction<Object> metricFunction;

    private LongMeasurementSource(@Nullable Object obj, ToLongFunction<Object> metricFunction) {
      this.objWeakRef = new WeakReference<>(obj);
      this.metricFunction = metricFunction;
    }
  }

  static final class AsyncMeasurementHandle {

    private final MeasurementsRecorder<?> measurementsRecorder;
    private final Attributes attributes;

    AsyncMeasurementHandle(MeasurementsRecorder<?> measurementsRecorder, Attributes attributes) {
      this.measurementsRecorder = measurementsRecorder;
      this.attributes = attributes;
    }

    void remove() {
      measurementsRecorder.removeMeasurement(attributes);
    }
  }
}
