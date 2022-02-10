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
import io.opentelemetry.instrumentation.api.internal.AsyncInstrumentRegistry;
import io.opentelemetry.instrumentation.api.internal.AsyncInstrumentRegistry.AsyncMeasurementHandle;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import javax.annotation.Nullable;

@SuppressWarnings("HashCodeToString")
final class OpenTelemetryFunctionTimer<T> implements FunctionTimer, RemovableMeter {

  private final Id id;
  private final TimeUnit baseTimeUnit;
  private final AsyncMeasurementHandle countMeasurementHandle;
  private final AsyncMeasurementHandle totalTimeMeasurementHandle;

  OpenTelemetryFunctionTimer(
      Id id,
      NamingConvention namingConvention,
      T obj,
      ToLongFunction<T> countFunction,
      ToDoubleFunction<T> totalTimeFunction,
      TimeUnit totalTimeFunctionUnit,
      TimeUnit baseTimeUnit,
      AsyncInstrumentRegistry asyncInstrumentRegistry) {

    this.id = id;
    this.baseTimeUnit = baseTimeUnit;

    String countMeterName = name(id, namingConvention) + ".count";
    String totalTimeMeterName = name(id, namingConvention) + ".sum";
    Attributes attributes = tagsAsAttributes(id, namingConvention);

    countMeasurementHandle =
        asyncInstrumentRegistry.buildLongCounter(
            countMeterName, description(id), /* baseUnit = */ "1", attributes, obj, countFunction);

    totalTimeMeasurementHandle =
        asyncInstrumentRegistry.buildDoubleCounter(
            totalTimeMeterName,
            description(id),
            getUnitString(baseTimeUnit),
            attributes,
            obj,
            val ->
                TimeUtils.convert(
                    totalTimeFunction.applyAsDouble(val), totalTimeFunctionUnit, baseTimeUnit));
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
