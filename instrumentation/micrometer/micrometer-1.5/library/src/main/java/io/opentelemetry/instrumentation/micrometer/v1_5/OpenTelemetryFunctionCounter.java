/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.api.incubator.metrics.internal.MetricCallbackRegistration.withoutContextClassLoader;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.baseUnit;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.name;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;
import static java.util.Collections.emptyList;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.api.metrics.DoubleCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleCounter;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.OpenTelemetryInstrument;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;

final class OpenTelemetryFunctionCounter<T> extends AbstractMeter
    implements FunctionCounter, RemovableMeter, OpenTelemetryInstrument {

  @Nullable private ObservableDoubleCounter observableCount;

  OpenTelemetryFunctionCounter(
      Id id,
      NamingConvention namingConvention,
      T obj,
      ToDoubleFunction<T> countFunction,
      Meter otelMeter,
      Bridging bridging) {
    super(id);

    String name = name(id, namingConvention);
    DoubleCounterBuilder builder =
        otelMeter
            .counterBuilder(name)
            .ofDoubles()
            .setDescription(bridging.description(name, id))
            .setUnit(baseUnit(id));
    DoubleMeasurementRecorder<T> callback =
        new DoubleMeasurementRecorder<>(obj, countFunction, tagsAsAttributes(id, namingConvention));
    observableCount = withoutContextClassLoader(() -> builder.buildWithCallback(callback));
  }

  @Override
  public double count() {
    UnsupportedReadLogger.logWarning();
    return Double.NaN;
  }

  @Override
  public Iterable<Measurement> measure() {
    UnsupportedReadLogger.logWarning();
    return emptyList();
  }

  @Override
  public void onRemove() {
    ObservableDoubleCounter observable;
    synchronized (this) {
      observable = observableCount;
      observableCount = null;
    }
    if (observable != null) {
      observable.close();
    }
  }
}
