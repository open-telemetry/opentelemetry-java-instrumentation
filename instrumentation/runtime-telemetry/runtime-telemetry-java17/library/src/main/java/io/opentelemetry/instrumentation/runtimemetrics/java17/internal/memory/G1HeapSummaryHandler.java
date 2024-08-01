/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java17.internal.memory;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimemetrics.java17.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.Constants;
import io.opentelemetry.instrumentation.runtimemetrics.java17.internal.RecordedEventHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import jdk.jfr.consumer.RecordedEvent;

/**
 * This class handles G1HeapSummary JFR events. For GC purposes they come in pairs. Basic heap
 * values are sourced from GCHeapSummary - this is young generational details This class is internal
 * and is hence not for public use. Its APIs are unstable and can change at any time.
 */
public final class G1HeapSummaryHandler implements RecordedEventHandler {
  private static final Logger logger = Logger.getLogger(G1HeapSummaryHandler.class.getName());

  private static final String EVENT_NAME = "jdk.G1HeapSummary";
  private static final String BEFORE = "Before GC";
  private static final String AFTER = "After GC";
  private static final String GC_ID = "gcId";
  private static final String EDEN_USED_SIZE = "edenUsedSize";
  private static final String EDEN_TOTAL_SIZE = "edenTotalSize";
  private static final String SURVIVOR_USED_SIZE = "survivorUsedSize";
  private static final String WHEN = "when";
  private static final Attributes ATTR_MEMORY_EDEN =
      Attributes.of(
          Constants.ATTR_MEMORY_TYPE, Constants.HEAP, Constants.ATTR_MEMORY_POOL, "G1 Eden Space");
  private static final Attributes ATTR_MEMORY_SURVIVOR =
      Attributes.of(
          Constants.ATTR_MEMORY_TYPE,
          Constants.HEAP,
          Constants.ATTR_MEMORY_POOL,
          "G1 Survivor Space");
  //  private static final Attributes ATTR_MEMORY_OLD_USED =
  //      Attributes.of(ATTR_TYPE, HEAP, ATTR_POOL, "G1 Old Gen"); // TODO needs jdk JFR support

  private final List<AutoCloseable> observables = new ArrayList<>();

  private volatile long usageEden = 0;
  private volatile long usageEdenAfter = 0;
  private volatile long usageSurvivor = 0;
  private volatile long usageSurvivorAfter = 0;
  private volatile long committedEden = 0;

  public G1HeapSummaryHandler(Meter meter) {
    observables.add(
        meter
            .upDownCounterBuilder(Constants.METRIC_NAME_MEMORY)
            .setDescription(Constants.METRIC_DESCRIPTION_MEMORY)
            .setUnit(Constants.BYTES)
            .buildWithCallback(
                measurement -> {
                  measurement.record(usageEden, ATTR_MEMORY_EDEN);
                  measurement.record(usageSurvivor, ATTR_MEMORY_SURVIVOR);
                }));
    observables.add(
        meter
            .upDownCounterBuilder(Constants.METRIC_NAME_MEMORY_AFTER)
            .setDescription(Constants.METRIC_DESCRIPTION_MEMORY_AFTER)
            .setUnit(Constants.BYTES)
            .buildWithCallback(
                measurement -> {
                  measurement.record(usageEdenAfter, ATTR_MEMORY_EDEN);
                  measurement.record(usageSurvivorAfter, ATTR_MEMORY_SURVIVOR);
                }));
    observables.add(
        meter
            .upDownCounterBuilder(Constants.METRIC_NAME_COMMITTED)
            .setDescription(Constants.METRIC_DESCRIPTION_COMMITTED)
            .setUnit(Constants.BYTES)
            .buildWithCallback(measurement -> measurement.record(committedEden, ATTR_MEMORY_EDEN)));
  }

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public JfrFeature getFeature() {
    return JfrFeature.MEMORY_POOL_METRICS;
  }

  @Override
  public void accept(RecordedEvent ev) {
    String when;
    if (ev.hasField(WHEN)) {
      when = ev.getString(WHEN);
    } else {
      logger.fine(String.format("G1 GC Event seen without when: %s", ev));
      return;
    }
    if (!(BEFORE.equals(when) || AFTER.equals(when))) {
      logger.fine(String.format("G1 GC Event seen where when is neither before nor after: %s", ev));
      return;
    }

    if (!ev.hasField(GC_ID)) {
      logger.fine(String.format("G1 GC Event seen without GC ID: %s", ev));
      return;
    }
    recordValues(ev, BEFORE.equals(when));
  }

  private void recordValues(RecordedEvent event, boolean before) {
    if (event.hasField(EDEN_USED_SIZE)) {
      if (before) {
        usageEden = event.getLong(EDEN_USED_SIZE);
      } else {
        usageEdenAfter = event.getLong(EDEN_USED_SIZE);
      }
    }

    if (event.hasField(SURVIVOR_USED_SIZE)) {
      if (before) {
        usageSurvivor = event.getLong(SURVIVOR_USED_SIZE);
      } else {
        usageSurvivorAfter = event.getLong(SURVIVOR_USED_SIZE);
      }
    }

    if (event.hasField(EDEN_TOTAL_SIZE)) {
      committedEden = event.getLong(EDEN_TOTAL_SIZE);
    }
  }

  @Override
  public void close() {
    RecordedEventHandler.closeObservables(observables);
  }
}
