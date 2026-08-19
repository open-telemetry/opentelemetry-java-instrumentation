/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal.memory;

import static io.opentelemetry.semconv.JvmAttributes.JVM_MEMORY_POOL_NAME;
import static io.opentelemetry.semconv.JvmAttributes.JVM_MEMORY_TYPE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.RecordedEventHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.annotation.Nullable;
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
      Attributes.of(JVM_MEMORY_TYPE, Constants.HEAP, JVM_MEMORY_POOL_NAME, "G1 Eden Space");
  private static final Attributes ATTR_MEMORY_SURVIVOR =
      Attributes.of(JVM_MEMORY_TYPE, Constants.HEAP, JVM_MEMORY_POOL_NAME, "G1 Survivor Space");
  //  private static final Attributes ATTR_MEMORY_OLD_USED =
  //      Attributes.of(ATTR_TYPE, HEAP, ATTR_POOL, "G1 Old Gen"); // TODO needs jdk JFR support

  private final List<AutoCloseable> observables = new ArrayList<>();
  private final Set<String> metricNames;
  private final boolean usageSelected;
  private final boolean usageAfterSelected;
  private final boolean committedSelected;

  private volatile long usageEden = 0;
  private volatile long usageEdenAfter = 0;
  private volatile long usageSurvivor = 0;
  private volatile long usageSurvivorAfter = 0;
  private volatile long committedEden = 0;

  @Nullable
  public static G1HeapSummaryHandler create(Meter meter, Predicate<String> metricNamePredicate) {
    Set<String> metricNames =
        RecordedEventHandler.selectMetricNames(
            metricNamePredicate,
            Constants.METRIC_NAME_MEMORY,
            Constants.METRIC_NAME_MEMORY_AFTER,
            Constants.METRIC_NAME_COMMITTED);
    return metricNames.isEmpty() ? null : new G1HeapSummaryHandler(meter, metricNames);
  }

  private G1HeapSummaryHandler(Meter meter, Set<String> metricNames) {
    this.metricNames = metricNames;
    usageSelected = metricNames.contains(Constants.METRIC_NAME_MEMORY);
    usageAfterSelected = metricNames.contains(Constants.METRIC_NAME_MEMORY_AFTER);
    committedSelected = metricNames.contains(Constants.METRIC_NAME_COMMITTED);
    if (usageSelected) {
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
    }
    if (usageAfterSelected) {
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
    }
    if (committedSelected) {
      observables.add(
          meter
              .upDownCounterBuilder(Constants.METRIC_NAME_COMMITTED)
              .setDescription(Constants.METRIC_DESCRIPTION_COMMITTED)
              .setUnit(Constants.BYTES)
              .buildWithCallback(
                  measurement -> measurement.record(committedEden, ATTR_MEMORY_EDEN)));
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
    boolean recordUsage = before ? usageSelected : usageAfterSelected;
    if (recordUsage && event.hasField(EDEN_USED_SIZE)) {
      if (before) {
        usageEden = event.getLong(EDEN_USED_SIZE);
      } else {
        usageEdenAfter = event.getLong(EDEN_USED_SIZE);
      }
    }

    if (recordUsage && event.hasField(SURVIVOR_USED_SIZE)) {
      if (before) {
        usageSurvivor = event.getLong(SURVIVOR_USED_SIZE);
      } else {
        usageSurvivorAfter = event.getLong(SURVIVOR_USED_SIZE);
      }
    }

    if (committedSelected && event.hasField(EDEN_TOTAL_SIZE)) {
      committedEden = event.getLong(EDEN_TOTAL_SIZE);
    }
  }

  @Override
  public void close() {
    RecordedEventHandler.closeObservables(observables);
  }
}
