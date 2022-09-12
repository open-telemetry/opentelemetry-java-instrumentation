/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.baseUnit;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.name;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleCounter;
import java.util.Collections;
import java.util.function.ToDoubleFunction;

final class OpenTelemetryFunctionCounter<T> extends AbstractMeter
    implements FunctionCounter, RemovableMeter {

  private final ObservableDoubleCounter observableCount;

  OpenTelemetryFunctionCounter(
      Id id,
      NamingConvention namingConvention,
      T obj,
      ToDoubleFunction<T> countFunction,
      Meter otelMeter) {
    super(id);

    String name = name(id, namingConvention);
    observableCount =
        otelMeter
            .counterBuilder(name)
            .ofDoubles()
            .setDescription(Bridging.description(id))
            .setUnit(baseUnit(id))
            .buildWithCallback(
                new DoubleMeasurementRecorder<>(
                    obj, countFunction, tagsAsAttributes(id, namingConvention)));
  }

  @Override
  public double count() {
    UnsupportedReadLogger.logWarning();
    return Double.NaN;
  }

  @Override
  public Iterable<Measurement> measure() {
    UnsupportedReadLogger.logWarning();
    return Collections.emptyList();
  }

  @Override
  public void onRemove() {
    observableCount.close();
  }
}
