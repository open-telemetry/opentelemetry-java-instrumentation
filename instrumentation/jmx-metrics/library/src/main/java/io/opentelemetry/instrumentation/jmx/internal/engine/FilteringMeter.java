package io.opentelemetry.instrumentation.jmx.internal.engine;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class FilteringMeter implements Meter {

  private static final Meter NOOP_METER = OpenTelemetry.noop().getMeter("noop");
  private static final ObservableLongMeasurement NOOP_LONG_MEASUREMENT = NOOP_METER.counterBuilder(
      "").buildObserver();
  private static final ObservableDoubleMeasurement NOOP_DOUBLE_MEASUREMENT = NOOP_METER.counterBuilder(
      "").ofDoubles().buildObserver();

  private final Meter delegate;
  private final IncludeExclude metrics;

  FilteringMeter(Meter delegate, IncludeExclude metrics) {
    this.delegate = delegate;
    this.metrics = metrics;
  }

  @Override
  public LongCounterBuilder counterBuilder(String s) {
    return (metrics.matches(s) ? delegate : NOOP_METER).counterBuilder(s);
  }

  @Override
  public LongUpDownCounterBuilder upDownCounterBuilder(String s) {
    return (metrics.matches(s) ? delegate : NOOP_METER).upDownCounterBuilder(s);
  }

  @Override
  public DoubleHistogramBuilder histogramBuilder(String s) {
    return (metrics.matches(s) ? delegate : NOOP_METER).histogramBuilder(s);
  }

  @Override
  public DoubleGaugeBuilder gaugeBuilder(String s) {
    return (metrics.matches(s) ? delegate : NOOP_METER).gaugeBuilder(s);
  }

  @Override
  public BatchCallback batchCallback(Runnable callback, ObservableMeasurement observableMeasurement,
      ObservableMeasurement... additionalMeasurements) {

    // The delegate implementation requires to have at least one (real) observable measurement, so
    // we need to call it only when there is at least one non-noop observable measurement.

    List<ObservableMeasurement> measurements = new ArrayList<>();

    if (isRealObservableMeasurement(observableMeasurement)) {
      measurements.add(observableMeasurement);
    }
    Arrays.stream(additionalMeasurements)
        .filter(FilteringMeter::isRealObservableMeasurement)
        .forEach(measurements::add);

    if (measurements.isEmpty()) {
      return new BatchCallback() {
        @Override
        public void close() {
        }
      };
    }

    if (measurements.size() == 1) {
      return delegate.batchCallback(callback, measurements.get(0));
    }

    return delegate.batchCallback(callback,
        measurements.get(0),
        measurements.subList(1, measurements.size()).toArray(new ObservableMeasurement[0]));
  }

  private static boolean isRealObservableMeasurement(ObservableMeasurement measurement) {
    // simple heuristic to check for no-op by reference equality, relying on the implementation detail that the no-op observable measurements are constants.
    return measurement != NOOP_LONG_MEASUREMENT && measurement != NOOP_DOUBLE_MEASUREMENT;
  }
}
