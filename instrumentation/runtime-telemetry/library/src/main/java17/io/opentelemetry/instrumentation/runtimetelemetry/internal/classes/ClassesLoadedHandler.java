/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal.classes;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.RecordedEventHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ClassesLoadedHandler implements RecordedEventHandler {
  /**
   * jvm.class.loaded is the total number of classes loaded since JVM start. See:
   * https://github.com/open-telemetry/semantic-conventions/blob/main/docs/runtime/jvm-metrics.md
   */
  private static final String METRIC_NAME_LOADED = "jvm.class.loaded";

  private static final String METRIC_NAME_UNLOADED = "jvm.class.unloaded";

  /**
   * jvm.class.count is the number of classes loaded at the time of jdk.ClassLoadingStatistics event
   * emission.
   */
  private static final String METRIC_NAME_CURRENT = "jvm.class.count";

  private static final String EVENT_NAME = "jdk.ClassLoadingStatistics";
  private static final String METRIC_DESCRIPTION_CURRENT = "Number of classes currently loaded.";
  private static final String METRIC_DESCRIPTION_LOADED =
      "Number of classes loaded since JVM start.";
  private static final String METRIC_DESCRIPTION_UNLOADED =
      "Number of classes unloaded since JVM start.";

  private final List<AutoCloseable> observables = new ArrayList<>();
  private final Set<String> metricNames;
  private final boolean loadedSelected;
  private final boolean unloadedSelected;

  private volatile long loaded = 0;
  private volatile long unloaded = 0;

  @Nullable
  public static ClassesLoadedHandler create(Meter meter, Predicate<String> metricNamePredicate) {
    Set<String> metricNames =
        RecordedEventHandler.selectMetricNames(
            metricNamePredicate, METRIC_NAME_LOADED, METRIC_NAME_UNLOADED, METRIC_NAME_CURRENT);
    return metricNames.isEmpty() ? null : new ClassesLoadedHandler(meter, metricNames);
  }

  private ClassesLoadedHandler(Meter meter, Set<String> metricNames) {
    this.metricNames = metricNames;
    boolean currentSelected = metricNames.contains(METRIC_NAME_CURRENT);
    loadedSelected = currentSelected || metricNames.contains(METRIC_NAME_LOADED);
    unloadedSelected = currentSelected || metricNames.contains(METRIC_NAME_UNLOADED);
    if (currentSelected) {
      observables.add(
          meter
              .upDownCounterBuilder(METRIC_NAME_CURRENT)
              .setDescription(METRIC_DESCRIPTION_CURRENT)
              .setUnit(Constants.UNIT_CLASSES)
              .buildWithCallback(measurement -> measurement.record(loaded - unloaded)));
    }
    if (metricNames.contains(METRIC_NAME_LOADED)) {
      observables.add(
          meter
              .counterBuilder(METRIC_NAME_LOADED)
              .setDescription(METRIC_DESCRIPTION_LOADED)
              .setUnit(Constants.UNIT_CLASSES)
              .buildWithCallback(measurement -> measurement.record(loaded)));
    }
    if (metricNames.contains(METRIC_NAME_UNLOADED)) {
      observables.add(
          meter
              .counterBuilder(METRIC_NAME_UNLOADED)
              .setDescription(METRIC_DESCRIPTION_UNLOADED)
              .setUnit(Constants.UNIT_CLASSES)
              .buildWithCallback(measurement -> measurement.record(unloaded)));
    }
  }

  @Override
  public void accept(RecordedEvent ev) {
    if (loadedSelected) {
      loaded = ev.getLong("loadedClassCount");
    }
    if (unloadedSelected) {
      unloaded = ev.getLong("unloadedClassCount");
    }
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public Set<String> getMetricNames() {
    return metricNames;
  }

  @Override
  public Optional<Duration> getPollingDuration() {
    return Optional.of(Duration.ofSeconds(1));
  }

  @Override
  public void close() {
    RecordedEventHandler.closeObservables(observables);
  }
}
