/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal.container;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.RecordedEventHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ContainerConfigurationHandler implements RecordedEventHandler {
  private static final String METRIC_NAME = "jvm.cpu.count";
  // Legacy metric name for backward compatibility with runtime-telemetry-java17 module
  private static final String LEGACY_METRIC_NAME = "jvm.cpu.limit";

  private static final String EVENT_NAME = "jdk.ContainerConfiguration";
  private static final String EFFECTIVE_CPU_COUNT = "effectiveCpuCount";

  private final List<AutoCloseable> observables = new ArrayList<>();
  private final String metricName;

  private volatile long value = 0L;

  @Nullable
  public static ContainerConfigurationHandler create(
      Meter meter, Predicate<String> metricNamePredicate, boolean useLegacyMetric) {
    String metricName = useLegacyMetric ? LEGACY_METRIC_NAME : METRIC_NAME;
    return metricNamePredicate.test(metricName)
        ? new ContainerConfigurationHandler(meter, useLegacyMetric)
        : null;
  }

  public ContainerConfigurationHandler(Meter meter, boolean useLegacyMetric) {
    metricName = useLegacyMetric ? LEGACY_METRIC_NAME : METRIC_NAME;
    var builder =
        useLegacyMetric
            ? meter.upDownCounterBuilder(LEGACY_METRIC_NAME).setUnit(Constants.ONE)
            : meter
                .upDownCounterBuilder(METRIC_NAME)
                .setUnit("{cpu}")
                .setDescription("Number of processors available to the Java virtual machine.");
    observables.add(builder.buildWithCallback(codm -> codm.record(value)));
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public Set<String> getMetricNames() {
    return Set.of(metricName);
  }

  @Override
  public void accept(RecordedEvent ev) {
    if (ev.hasField(EFFECTIVE_CPU_COUNT)) {
      value = ev.getLong(EFFECTIVE_CPU_COUNT);
    }
  }

  @Override
  public void close() {
    RecordedEventHandler.closeObservables(observables);
  }
}
