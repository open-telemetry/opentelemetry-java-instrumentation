/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.util.TimeUtils;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleCounter;
import io.opentelemetry.api.metrics.ObservableLongCounter;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.OpenTelemetryInstrument;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

final class OpenTelemetryFunctionTimer<T> extends AbstractMeter
    implements FunctionTimer, RemovableMeter, OpenTelemetryInstrument {
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
    super(id);
    this.baseTimeUnit = baseTimeUnit;

    String name = Bridging.name(id, namingConvention);
    Attributes attributes = Bridging.tagsAsAttributes(id, namingConvention);

    this.observableCount =
        otelMeter
            .counterBuilder(name + ".count")
            .setDescription(Bridging.description(id))
            .setUnit("{invocation}")
            .buildWithCallback(new LongMeasurementRecorder<>(obj, countFunction, attributes));

    this.observableTotalTime =
        otelMeter
            .counterBuilder(name + ".sum")
            .ofDoubles()
            .setDescription(Bridging.description(id))
            .setUnit(TimeUnitHelper.getUnitString(baseTimeUnit))
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
  public void onRemove() {
    observableCount.close();
    observableTotalTime.close();
  }
}
