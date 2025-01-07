/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal.threads;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.RecordedEventHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ThreadCountHandler implements RecordedEventHandler {
  private static final String METRIC_NAME = "jvm.thread.count";
  private static final String EVENT_NAME = "jdk.JavaThreadStatistics";
  private static final String METRIC_DESCRIPTION = "Number of executing threads";
  private static final Attributes ATTR_DAEMON_TRUE = Attributes.of(Constants.ATTR_DAEMON, true);
  private static final Attributes ATTR_DAEMON_FALSE = Attributes.of(Constants.ATTR_DAEMON, false);

  private final List<AutoCloseable> observables = new ArrayList<>();

  private volatile long activeCount = 0;
  private volatile long daemonCount = 0;

  public ThreadCountHandler(Meter meter) {
    observables.add(
        meter
            .upDownCounterBuilder(METRIC_NAME)
            .setDescription(METRIC_DESCRIPTION)
            .setUnit(Constants.UNIT_THREADS)
            .buildWithCallback(
                measurement -> {
                  long d = daemonCount;
                  measurement.record(d, ATTR_DAEMON_TRUE);
                  measurement.record(activeCount - d, ATTR_DAEMON_FALSE);
                }));
  }

  @Override
  public void accept(RecordedEvent ev) {
    activeCount = ev.getLong("activeCount");
    daemonCount = ev.getLong("daemonCount");
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public JfrFeature getFeature() {
    return JfrFeature.THREAD_METRICS;
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
