/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.name;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;
import static java.util.Collections.emptyList;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.ObservableCallbackRegistrar;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.OpenTelemetryInstrument;
import java.util.concurrent.TimeUnit;

final class OpenTelemetryLongTaskTimer extends DefaultLongTaskTimer
    implements RemovableMeter, OpenTelemetryInstrument {

  private final DistributionStatisticConfig distributionStatisticConfig;
  private final ObservableCallbackRegistrar.Registration<ObservableLongUpDownCounter>
      observableActiveTasks;
  private final ObservableCallbackRegistrar.Registration<ObservableDoubleUpDownCounter>
      observableDuration;
  private boolean removed;

  OpenTelemetryLongTaskTimer(
      Id id,
      NamingConvention namingConvention,
      Clock clock,
      TimeUnit baseTimeUnit,
      DistributionStatisticConfig distributionStatisticConfig,
      Meter otelMeter,
      Bridging bridging,
      ObservableCallbackRegistrar callbackRegistrar) {
    super(id, clock, baseTimeUnit, distributionStatisticConfig, false);

    this.distributionStatisticConfig = distributionStatisticConfig;

    String name = name(id, namingConvention);
    Attributes attributes = tagsAsAttributes(id, namingConvention);

    LongUpDownCounterBuilder activeTasksBuilder =
        otelMeter
            .upDownCounterBuilder(name + ".active")
            .setDescription(bridging.description(name + ".active", id))
            .setUnit("{tasks}");
    LongMeasurementRecorder<OpenTelemetryLongTaskTimer> activeTasksCallback =
        new LongMeasurementRecorder<>(this, DefaultLongTaskTimer::activeTasks, attributes);
    observableActiveTasks =
        callbackRegistrar.register(
            activeTasksCallback,
            CallbackClassLoader.find(id),
            activeTasksBuilder::buildWithCallback);

    DoubleUpDownCounterBuilder durationBuilder =
        otelMeter
            .upDownCounterBuilder(name + ".duration")
            .ofDoubles()
            .setDescription(bridging.description(name + ".duration", id))
            .setUnit(TimeUnitHelper.getUnitString(baseTimeUnit));
    DoubleMeasurementRecorder<OpenTelemetryLongTaskTimer> durationCallback =
        new DoubleMeasurementRecorder<>(this, t -> t.duration(t.baseTimeUnit()), attributes);
    observableDuration =
        callbackRegistrar.register(
            durationCallback, CallbackClassLoader.find(id), durationBuilder::buildWithCallback);
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
    observableActiveTasks.close(ObservableLongUpDownCounter::close);
    observableDuration.close(ObservableDoubleUpDownCounter::close);
  }

  boolean isUsingMicrometerHistograms() {
    return distributionStatisticConfig.isPublishingPercentiles()
        || distributionStatisticConfig.isPublishingHistogram();
  }
}
