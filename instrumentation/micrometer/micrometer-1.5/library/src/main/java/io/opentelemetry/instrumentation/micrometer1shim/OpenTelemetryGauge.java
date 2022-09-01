/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer1shim;

import static io.opentelemetry.instrumentation.micrometer1shim.Bridging.baseUnit;
import static io.opentelemetry.instrumentation.micrometer1shim.Bridging.name;
import static io.opentelemetry.instrumentation.micrometer1shim.Bridging.tagsAsAttributes;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import java.util.Collections;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;

final class OpenTelemetryGauge<T> extends AbstractMeter implements Gauge, RemovableMeter {

  private final ObservableDoubleGauge observableGauge;

  OpenTelemetryGauge(
      Id id,
      NamingConvention namingConvention,
      @Nullable T obj,
      ToDoubleFunction<T> objMetric,
      Meter otelMeter) {
    super(id);

    String name = name(id, namingConvention);
    observableGauge =
        otelMeter
            .gaugeBuilder(name)
            .setDescription(Bridging.description(id))
            .setUnit(baseUnit(id))
            .buildWithCallback(
                new DoubleMeasurementRecorder<>(
                    obj, objMetric, tagsAsAttributes(id, namingConvention)));
  }

  @Override
  public double value() {
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
    observableGauge.close();
  }
}
