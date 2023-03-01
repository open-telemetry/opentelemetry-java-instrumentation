/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.classes;

import static io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.Constants.UNIT_CLASSES;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.JfrFeature;
import io.opentelemetry.instrumentation.runtimetelemetryjfr.internal.RecordedEventHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ClassesLoadedHandler implements RecordedEventHandler {
  /**
   * process.runtime.jvm.classes.loaded is the total number of classes loaded since JVM start. See:
   * https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/runtime-environment-metrics.md#jvm-metrics
   */
  private static final String METRIC_NAME_LOADED = "process.runtime.jvm.classes.loaded";

  private static final String METRIC_NAME_UNLOADED = "process.runtime.jvm.classes.unloaded";
  /**
   * process.runtime.jvm.classes.current_loaded is the number of classes loaded at the time of
   * jdk.ClassLoadingStatistics event emission.
   */
  private static final String METRIC_NAME_CURRENT = "process.runtime.jvm.classes.current_loaded";

  private static final String EVENT_NAME = "jdk.ClassLoadingStatistics";
  private static final String METRIC_DESCRIPTION_CURRENT = "Number of classes currently loaded";
  private static final String METRIC_DESCRIPTION_LOADED =
      "Number of classes loaded since JVM start";
  private static final String METRIC_DESCRIPTION_UNLOADED =
      "Number of classes unloaded since JVM start";

  private final List<AutoCloseable> observables = new ArrayList<>();

  private volatile long loaded = 0;
  private volatile long unloaded = 0;

  public ClassesLoadedHandler(Meter meter) {
    observables.add(
        meter
            .upDownCounterBuilder(METRIC_NAME_CURRENT)
            .setDescription(METRIC_DESCRIPTION_CURRENT)
            .setUnit(UNIT_CLASSES)
            .buildWithCallback(measurement -> measurement.record(loaded - unloaded)));
    observables.add(
        meter
            .counterBuilder(METRIC_NAME_LOADED)
            .setDescription(METRIC_DESCRIPTION_LOADED)
            .setUnit(UNIT_CLASSES)
            .buildWithCallback(measurement -> measurement.record(loaded)));
    observables.add(
        meter
            .counterBuilder(METRIC_NAME_UNLOADED)
            .setDescription(METRIC_DESCRIPTION_UNLOADED)
            .setUnit(UNIT_CLASSES)
            .buildWithCallback(measurement -> measurement.record(unloaded)));
  }

  @Override
  public void accept(RecordedEvent ev) {
    loaded = ev.getLong("loadedClassCount");
    unloaded = ev.getLong("unloadedClassCount");
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public JfrFeature getFeature() {
    return JfrFeature.CLASS_LOAD_METRICS;
  }

  @Override
  public Optional<Duration> getPollingDuration() {
    return Optional.of(Duration.ofSeconds(1));
  }

  @Override
  public void close() {
    closeObservables(observables);
  }
}
