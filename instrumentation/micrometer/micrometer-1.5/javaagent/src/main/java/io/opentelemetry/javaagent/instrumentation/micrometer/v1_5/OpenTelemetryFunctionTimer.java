/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.description;
import static io.opentelemetry.javaagent.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;

import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Statistic;
import io.micrometer.core.instrument.util.MeterEquivalence;
import io.micrometer.core.instrument.util.TimeUtils;
import io.opentelemetry.api.common.Attributes;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

@SuppressWarnings("HashCodeToString")
final class OpenTelemetryFunctionTimer<T> implements FunctionTimer, RemovableMeter {

  private final Id id;
  private final Attributes attributes;
  private final AsyncInstrumentRegistry asyncInstrumentRegistry;
  private final String countMeterName;
  private final String totalTimeMeterName;

  OpenTelemetryFunctionTimer(
      Id id,
      T obj,
      ToLongFunction<T> countFunction,
      ToDoubleFunction<T> totalTimeFunction,
      TimeUnit totalTimeFunctionUnit,
      AsyncInstrumentRegistry asyncInstrumentRegistry) {
    this.id = id;
    this.attributes = tagsAsAttributes(id);
    this.asyncInstrumentRegistry = asyncInstrumentRegistry;

    countMeterName = id.getName() + "." + Statistic.COUNT.getTagValueRepresentation();
    totalTimeMeterName = id.getName() + "." + Statistic.TOTAL_TIME.getTagValueRepresentation();

    asyncInstrumentRegistry.buildLongCounter(
        countMeterName, description(id), /* baseUnit = */ "1", attributes, obj, countFunction);

    asyncInstrumentRegistry.buildGauge(
        totalTimeMeterName,
        description(id),
        /* baseUnit = */ "ms",
        attributes,
        obj,
        new ConvertToMillisDecorator<>(totalTimeFunction, totalTimeFunctionUnit));
  }

  @Override
  public double count() {
    UnsupportedReadLogger.logWarning();
    return 0;
  }

  @Override
  public double totalTime(TimeUnit unit) {
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
    asyncInstrumentRegistry.removeLongCounter(countMeterName, attributes);
    asyncInstrumentRegistry.removeGauge(totalTimeMeterName, attributes);
  }

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(Object o) {
    return MeterEquivalence.equals(this, o);
  }

  @Override
  public int hashCode() {
    return MeterEquivalence.hashCode(this);
  }

  private static final class ConvertToMillisDecorator<T> implements ToDoubleFunction<T> {

    private final ToDoubleFunction<T> original;
    private final TimeUnit originalUnit;

    private ConvertToMillisDecorator(ToDoubleFunction<T> original, TimeUnit originalUnit) {
      this.original = original;
      this.originalUnit = originalUnit;
    }

    @Override
    public double applyAsDouble(T value) {
      double time = original.applyAsDouble(value);
      return TimeUtils.convert(time, originalUnit, TimeUnit.MILLISECONDS);
    }
  }
}
