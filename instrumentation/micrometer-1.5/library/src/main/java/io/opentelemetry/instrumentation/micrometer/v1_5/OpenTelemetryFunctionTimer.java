/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.description;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.statisticInstrumentName;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;

import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.micrometer.core.instrument.util.TimeUtils;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.micrometer.v1_5.AsyncInstrumentRegistry.AsyncMeasurementHandle;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import javax.annotation.Nullable;

@SuppressWarnings("HashCodeToString")
final class OpenTelemetryFunctionTimer<T> implements FunctionTimer, RemovableMeter {

  private final Id id;
  private final AsyncMeasurementHandle countMeasurementHandle;
  private final AsyncMeasurementHandle totalTimeMeasurementHandle;

  OpenTelemetryFunctionTimer(
      Id id,
      T obj,
      ToLongFunction<T> countFunction,
      ToDoubleFunction<T> totalTimeFunction,
      TimeUnit totalTimeFunctionUnit,
      AsyncInstrumentRegistry asyncInstrumentRegistry) {
    this.id = id;

    String countMeterName = statisticInstrumentName(id, Statistic.COUNT);
    String totalTimeMeterName = statisticInstrumentName(id, Statistic.TOTAL_TIME);
    Attributes attributes = tagsAsAttributes(id);

    countMeasurementHandle =
        asyncInstrumentRegistry.buildLongCounter(
            countMeterName, description(id), /* baseUnit = */ "1", attributes, obj, countFunction);

    totalTimeMeasurementHandle =
        asyncInstrumentRegistry.buildDoubleCounter(
            totalTimeMeterName,
            description(id),
            /* baseUnit = */ "ms",
            attributes,
            obj,
            val ->
                TimeUtils.convert(
                    totalTimeFunction.applyAsDouble(val),
                    totalTimeFunctionUnit,
                    TimeUnit.MILLISECONDS));
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
    return TimeUnit.MILLISECONDS;
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
    countMeasurementHandle.remove();
    totalTimeMeasurementHandle.remove();
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
