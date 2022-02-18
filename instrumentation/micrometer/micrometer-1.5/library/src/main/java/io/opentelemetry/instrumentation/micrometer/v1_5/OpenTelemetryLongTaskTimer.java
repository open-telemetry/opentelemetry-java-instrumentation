/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.description;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.name;
import static io.opentelemetry.instrumentation.micrometer.v1_5.Bridging.tagsAsAttributes;
import static io.opentelemetry.instrumentation.micrometer.v1_5.TimeUnitHelper.getUnitString;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.api.internal.AsyncInstrumentRegistry;
import io.opentelemetry.instrumentation.api.internal.AsyncInstrumentRegistry.AsyncMeasurementHandle;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

final class OpenTelemetryLongTaskTimer extends DefaultLongTaskTimer implements RemovableMeter {

  private final DistributionStatisticConfig distributionStatisticConfig;
  private final AsyncMeasurementHandle activeTasksHandle;
  private final AsyncMeasurementHandle durationHandle;

  OpenTelemetryLongTaskTimer(
      Id id,
      NamingConvention namingConvention,
      Clock clock,
      TimeUnit baseTimeUnit,
      DistributionStatisticConfig distributionStatisticConfig,
      AsyncInstrumentRegistry asyncInstrumentRegistry) {
    super(id, clock, baseTimeUnit, distributionStatisticConfig, false);

    this.distributionStatisticConfig = distributionStatisticConfig;

    String conventionName = name(id, namingConvention);
    Attributes attributes = tagsAsAttributes(id, namingConvention);
    this.activeTasksHandle =
        asyncInstrumentRegistry.buildUpDownLongCounter(
            conventionName + ".active",
            description(id),
            "tasks",
            attributes,
            this,
            DefaultLongTaskTimer::activeTasks);
    this.durationHandle =
        asyncInstrumentRegistry.buildUpDownDoubleCounter(
            conventionName + ".duration",
            description(id),
            getUnitString(baseTimeUnit),
            attributes,
            this,
            t -> t.duration(baseTimeUnit));
  }

  @Override
  public Iterable<Measurement> measure() {
    UnsupportedReadLogger.logWarning();
    return Collections.emptyList();
  }

  @Override
  public void onRemove() {
    activeTasksHandle.remove();
    durationHandle.remove();
  }

  boolean isUsingMicrometerHistograms() {
    return distributionStatisticConfig.isPublishingPercentiles()
        || distributionStatisticConfig.isPublishingHistogram();
  }
}
