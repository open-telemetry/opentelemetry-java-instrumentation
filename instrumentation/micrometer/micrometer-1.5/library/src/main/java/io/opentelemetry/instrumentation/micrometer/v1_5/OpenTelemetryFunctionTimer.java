/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.description;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.name;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;
import static io.opentelemetry.instrumentation.micrometer.v1_5.TimeUnitHelper.getUnitString;

import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.micrometer.core.instrument.util.TimeUtils;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleCounter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import javax.annotation.Nullable;

@SuppressWarnings("HashCodeToString")
final class OpenTelemetryFunctionTimer<T> implements FunctionTimer, RemovableMeter {

  private final Id id;
  private final TimeUnit baseTimeUnit;
  private final ObservableLongCounter observableCount;
  private final ObservableDoubleCounter observableTotalTime;

  OpenTelemetryFunctionTimer(
      Id id,
      NamingConvention namingConvention,
      T obj,
      ToLongFunction<T> countFunction,
      ToDoubleFunction<T> totalTimeFunction,
      TimeUnit totalTimeFunctionUnit,
      TimeUnit baseTimeUnit,
      Meter otelMeter) {

    this.id = id;
    this.baseTimeUnit = baseTimeUnit;

    String name = name(id, namingConvention);
    Attributes attributes = tagsAsAttributes(id, namingConvention);

    this.observableCount =
        otelMeter
            .counterBuilder(name + ".count")
            .setDescription(description(name, id))
            .setUnit("1")
            .buildWithCallback(new LongMeasurementRecorder<>(obj, countFunction, attributes));

    this.observableTotalTime =
        otelMeter
            .counterBuilder(name + ".sum")
            .ofDoubles()
            .setDescription(description(name, id))
            .setUnit(getUnitString(baseTimeUnit))
            .buildWithCallback(
                new DoubleMeasurementRecorder<>(
                    obj,
                    val ->
                        TimeUtils.convert(
                            totalTimeFunction.applyAsDouble(val),
                            totalTimeFunctionUnit,
                            baseTimeUnit),
                    attributes));
  }

  @Override
  public double count() {
    UnsupportedReadLogger.logWarning();
    return Double.NaN;
  }

  @Override
  public double totalTime(TimeUnit unit) {
    UnsupportedReadLogger.logWarning();
    return Double.NaN;
  }

  @Override
  public double mean(TimeUnit unit) {
    UnsupportedReadLogger.logWarning();
    return Double.NaN;
  }

  @Override
  public TimeUnit baseTimeUnit() {
    return baseTimeUnit;
  }

  @Override
  public Iterable<Measurement> measure() {
    UnsupportedReadLogger.logWarning();
    return Collections.emptyList();
  }

  @Override
  public Id getId() {
    return id;
  }

  @Override
  public void onRemove() {
    observableCount.close();
    observableTotalTime.close();
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
