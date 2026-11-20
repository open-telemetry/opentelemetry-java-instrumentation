/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.baseUnit;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.name;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;
import static java.util.Collections.emptyList;

import io.micrometer.core.instrument.AbstractMeter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.config.NamingConvention;
import io.opentelemetry.api.metrics.DoubleGaugeBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleGauge;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.ObservableCallbackRegistrar;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.OpenTelemetryInstrument;
import java.util.function.ToDoubleFunction;
import javax.annotation.Nullable;

final class OpenTelemetryGauge<T> extends AbstractMeter
    implements Gauge, RemovableMeter, OpenTelemetryInstrument {

  private final ObservableCallbackRegistrar.Registration<ObservableDoubleGauge> observableGauge;
  private boolean removed;

  OpenTelemetryGauge(
      Id id,
      NamingConvention namingConvention,
      @Nullable T obj,
      ToDoubleFunction<T> objMetric,
      Meter otelMeter,
      Bridging bridging,
      ObservableCallbackRegistrar callbackRegistrar) {
    super(id);

    String name = name(id, namingConvention);
    DoubleGaugeBuilder builder =
        otelMeter
            .gaugeBuilder(name)
            .setDescription(bridging.description(name, id))
            .setUnit(baseUnit(id));
    DoubleMeasurementRecorder<T> callback =
        new DoubleMeasurementRecorder<>(obj, objMetric, tagsAsAttributes(id, namingConvention));
    observableGauge =
        callbackRegistrar.register(callback, id.getClass(), builder::buildWithCallback);
  }

  @Override
  public double value() {
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
    synchronized (this) {
      if (removed) {
        return;
      }
      removed = true;
    }
    observableGauge.close(ObservableDoubleGauge::close);
  }
}
