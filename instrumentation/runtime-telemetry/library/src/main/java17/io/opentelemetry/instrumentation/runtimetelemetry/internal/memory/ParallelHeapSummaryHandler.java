/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal.memory;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.RecordedEventHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;

/**
 * This class handles G1HeapSummary JFR events. For GC purposes they come in pairs. Basic heap
 * values are sourced from GCHeapSummary - this is young generational details
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ParallelHeapSummaryHandler implements RecordedEventHandler {
  private static final Logger logger = Logger.getLogger(ParallelHeapSummaryHandler.class.getName());
  private static final String EVENT_NAME = "jdk.PSHeapSummary";
  private static final String BEFORE = "Before GC";
  private static final String AFTER = "After GC";
  private static final String GC_ID = "gcId";
  private static final String WHEN = "when";
  private static final String SIZE = "size";
  private static final Attributes ATTR_MEMORY_EDEN =
      Attributes.of(
          Constants.ATTR_MEMORY_TYPE, Constants.HEAP, Constants.ATTR_MEMORY_POOL, "PS Eden Space");
  private static final Attributes ATTR_MEMORY_SURVIVOR =
      Attributes.of(
          Constants.ATTR_MEMORY_TYPE,
          Constants.HEAP,
          Constants.ATTR_MEMORY_POOL,
          "PS Survivor Space");
  private static final Attributes ATTR_MEMORY_OLD =
      Attributes.of(
          Constants.ATTR_MEMORY_TYPE, Constants.HEAP, Constants.ATTR_MEMORY_POOL, "PS Old Gen");

  private final List<AutoCloseable> observables = new ArrayList<>();
  private final Set<String> metricNames;
  private final boolean usageSelected;
  private final boolean usageAfterSelected;
  private final boolean committedSelected;
  private final boolean limitSelected;

  private volatile long usageEden = 0;
  private volatile long usageEdenAfter = 0;
  private volatile long usageSurvivor = 0;
  private volatile long usageSurvivorAfter = 0;
  private volatile long usageOld = 0;
  private volatile long usageOldAfter = 0;
  private volatile long committedOld = 0;
  private volatile long committedSurvivor = 0;
  private volatile long committedEden = 0;
  private volatile long limitOld = 0;
  private volatile long limitYoung = 0;

  @Nullable
  public static ParallelHeapSummaryHandler create(
      Meter meter, Predicate<String> metricNamePredicate) {
    Set<String> metricNames =
        RecordedEventHandler.selectMetricNames(
            metricNamePredicate,
            Constants.METRIC_NAME_MEMORY,
            Constants.METRIC_NAME_MEMORY_AFTER,
            Constants.METRIC_NAME_COMMITTED,
            Constants.METRIC_NAME_MEMORY_LIMIT);
    return metricNames.isEmpty() ? null : new ParallelHeapSummaryHandler(meter, metricNames);
  }

  private ParallelHeapSummaryHandler(Meter meter, Set<String> metricNames) {
    this.metricNames = metricNames;
    usageSelected = metricNames.contains(Constants.METRIC_NAME_MEMORY);
    usageAfterSelected = metricNames.contains(Constants.METRIC_NAME_MEMORY_AFTER);
    committedSelected = metricNames.contains(Constants.METRIC_NAME_COMMITTED);
    limitSelected = metricNames.contains(Constants.METRIC_NAME_MEMORY_LIMIT);
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
                    measurement.record(usageOld, ATTR_MEMORY_OLD);
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
                    measurement.record(usageOldAfter, ATTR_MEMORY_OLD);
                  }));
    }
    if (committedSelected) {
      observables.add(
          meter
              .upDownCounterBuilder(Constants.METRIC_NAME_COMMITTED)
              .setDescription(Constants.METRIC_DESCRIPTION_COMMITTED)
              .setUnit(Constants.BYTES)
              .buildWithCallback(
                  measurement -> {
                    measurement.record(committedOld, ATTR_MEMORY_OLD);
                    measurement.record(committedEden, ATTR_MEMORY_EDEN);
                    measurement.record(committedSurvivor, ATTR_MEMORY_SURVIVOR);
                  }));
    }
    if (limitSelected) {
      observables.add(
          meter
              .upDownCounterBuilder(Constants.METRIC_NAME_MEMORY_LIMIT)
              .setDescription(Constants.METRIC_DESCRIPTION_MEMORY_LIMIT)
              .setUnit(Constants.BYTES)
              .buildWithCallback(
                  measurement -> {
                    measurement.record(limitOld, ATTR_MEMORY_OLD);
                    measurement.record(limitYoung, ATTR_MEMORY_EDEN);
                    measurement.record(limitYoung, ATTR_MEMORY_SURVIVOR);
                  }));
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
      logger.fine(String.format("Parallel GC Event seen without when: %s", ev));
      return;
    }
    if (!(BEFORE.equals(when) || AFTER.equals(when))) {
      logger.fine(
          String.format("Parallel GC Event seen where when is neither before nor after: %s", ev));
      return;
    }

    if (!ev.hasField(GC_ID)) {
      logger.fine(String.format("Parallel GC Event seen without GC ID: %s", ev));
      return;
    }
    recordValues(ev, BEFORE.equals(when));
  }

  private static void doIfAvailable(
      RecordedEvent event, String field, Consumer<RecordedObject> closure) {
    if (!event.hasField(field)) {
      return;
    }
    Object value = event.getValue(field);
    if (value instanceof RecordedObject recordedObject) {
      closure.accept(recordedObject);
    }
  }

  private void recordValues(RecordedEvent event, boolean before) {
    boolean recordUsage = before ? usageSelected : usageAfterSelected;

    if (recordUsage || committedSelected) {
      doIfAvailable(
          event,
          "edenSpace",
          edenSpace -> {
            if (recordUsage && edenSpace.hasField(Constants.USED)) {
              if (before) {
                usageEden = edenSpace.getLong(Constants.USED);
              } else {
                usageEdenAfter = edenSpace.getLong(Constants.USED);
              }
            }
            if (committedSelected && edenSpace.hasField(SIZE)) {
              committedEden = edenSpace.getLong(SIZE);
            }
          });

      doIfAvailable(
          event,
          "fromSpace",
          fromSpace -> {
            if (recordUsage && fromSpace.hasField(Constants.USED)) {
              if (before) {
                usageSurvivor = fromSpace.getLong(Constants.USED);
              } else {
                usageSurvivorAfter = fromSpace.getLong(Constants.USED);
              }
            }
            if (committedSelected && fromSpace.hasField(SIZE)) {
              committedSurvivor = fromSpace.getLong(SIZE);
            }
          });
    }

    if (recordUsage) {
      doIfAvailable(
          event,
          "oldObjectSpace",
          oldObjectSpace -> {
            if (oldObjectSpace.hasField(Constants.USED)) {
              if (before) {
                usageOld = oldObjectSpace.getLong(Constants.USED);
              } else {
                usageOldAfter = oldObjectSpace.getLong(Constants.USED);
              }
            }
          });
    }

    if (committedSelected || limitSelected) {
      doIfAvailable(
          event,
          "oldSpace",
          oldSpace -> {
            if (committedSelected && oldSpace.hasField(Constants.COMMITTED_SIZE)) {
              committedOld = oldSpace.getLong(Constants.COMMITTED_SIZE);
            }
            if (limitSelected && oldSpace.hasField(Constants.RESERVED_SIZE)) {
              limitOld = oldSpace.getLong(Constants.RESERVED_SIZE);
            }
          });
    }

    if (limitSelected) {
      doIfAvailable(
          event,
          "youngSpace",
          oldSpace -> {
            if (oldSpace.hasField(Constants.RESERVED_SIZE)) {
              limitYoung = oldSpace.getLong(Constants.RESERVED_SIZE);
            }
          });
    }
  }

  @Override
  public void close() {
    RecordedEventHandler.closeObservables(observables);
  }
}
