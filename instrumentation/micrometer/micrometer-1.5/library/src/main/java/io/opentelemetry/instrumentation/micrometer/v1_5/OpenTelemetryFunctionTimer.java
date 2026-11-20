/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static java.util.Collections.emptyList;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.TimeUtils;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleCounter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.instrumentation.api.incubator.metrics.internal.MetricCallbackRegistration;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.OpenTelemetryInstrument;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import javax.annotation.Nullable;

final class OpenTelemetryFunctionTimer<T> extends AbstractMeter
    implements FunctionTimer, RemovableMeter, OpenTelemetryInstrument {
  private final TimeUnit baseTimeUnit;
  @Nullable private ObservableLongCounter observableCount;
  @Nullable private ObservableDoubleCounter observableTotalTime;

  OpenTelemetryFunctionTimer(
      Id id,
      NamingConvention namingConvention,
      T obj,
      ToLongFunction<T> countFunction,
      ToDoubleFunction<T> totalTimeFunction,
      TimeUnit totalTimeFunctionUnit,
      TimeUnit baseTimeUnit,
      Meter otelMeter,
      Bridging bridging) {
    super(id);
    this.baseTimeUnit = baseTimeUnit;

    String name = Bridging.name(id, namingConvention);
    Attributes attributes = Bridging.tagsAsAttributes(id, namingConvention);

    LongCounterBuilder countBuilder =
        otelMeter
            .counterBuilder(name + ".count")
            .setDescription(bridging.description(name + ".count", id))
            .setUnit("{invocation}");
    LongMeasurementRecorder<T> countCallback =
        new LongMeasurementRecorder<>(obj, countFunction, attributes);
    observableCount =
        MetricCallbackRegistration.withoutContextClassLoader(
            () -> countBuilder.buildWithCallback(countCallback));

    DoubleCounterBuilder totalTimeBuilder =
        otelMeter
            .counterBuilder(name + ".sum")
            .ofDoubles()
            .setDescription(bridging.description(name + ".sum", id))
            .setUnit(TimeUnitHelper.getUnitString(baseTimeUnit));
    DoubleMeasurementRecorder<T> totalTimeCallback =
        new DoubleMeasurementRecorder<>(
            obj,
            val ->
                TimeUtils.convert(
                    totalTimeFunction.applyAsDouble(val), totalTimeFunctionUnit, baseTimeUnit),
            attributes);
    observableTotalTime =
        MetricCallbackRegistration.withoutContextClassLoader(
            () -> totalTimeBuilder.buildWithCallback(totalTimeCallback));
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
    return emptyList();
  }

  @Override
  public synchronized void onRemove() {
    if (observableCount != null) {
      observableCount.close();
      observableCount = null;
    }
    if (observableTotalTime != null) {
      observableTotalTime.close();
      observableTotalTime = null;
    }
  }
}
