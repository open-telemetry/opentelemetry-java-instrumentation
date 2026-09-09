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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;

/**
 * This class handles GCHeapConfiguration JFR events.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MetaspaceSummaryHandler implements RecordedEventHandler {
  private static final String EVENT_NAME = "jdk.MetaspaceSummary";

  private static final Attributes ATTR_MEMORY_METASPACE =
      Attributes.of(JVM_MEMORY_TYPE, Constants.NON_HEAP, JVM_MEMORY_POOL_NAME, "Metaspace");
  private static final Attributes ATTR_MEMORY_COMPRESSED_CLASS_SPACE =
      Attributes.of(
          JVM_MEMORY_TYPE, Constants.NON_HEAP, JVM_MEMORY_POOL_NAME, "Compressed Class Space");

  private final List<AutoCloseable> observables = new ArrayList<>();
  private final Set<String> metricNames;
  private final boolean usageSelected;
  private final boolean committedSelected;
  private final boolean limitSelected;

  private volatile long classUsage = 0;
  private volatile long classCommitted = 0;
  private volatile long totalUsage = 0;
  private volatile long totalCommitted = 0;
  private volatile long classLimit = 0;
  private volatile long totalLimit = 0;

  @Nullable
  public static MetaspaceSummaryHandler create(Meter meter, Predicate<String> metricNamePredicate) {
    Set<String> metricNames =
        RecordedEventHandler.selectMetricNames(
            metricNamePredicate,
            Constants.METRIC_NAME_MEMORY,
            Constants.METRIC_NAME_COMMITTED,
            Constants.METRIC_NAME_MEMORY_LIMIT);
    return metricNames.isEmpty() ? null : new MetaspaceSummaryHandler(meter, metricNames);
  }

  private MetaspaceSummaryHandler(Meter meter, Set<String> metricNames) {
    this.metricNames = metricNames;
    usageSelected = metricNames.contains(Constants.METRIC_NAME_MEMORY);
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
                    measurement.record(classUsage, ATTR_MEMORY_COMPRESSED_CLASS_SPACE);
                    measurement.record(totalUsage, ATTR_MEMORY_METASPACE);
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
                    measurement.record(classCommitted, ATTR_MEMORY_COMPRESSED_CLASS_SPACE);
                    measurement.record(totalCommitted, ATTR_MEMORY_METASPACE);
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
                    measurement.record(classLimit, ATTR_MEMORY_COMPRESSED_CLASS_SPACE);
                    measurement.record(totalLimit, ATTR_MEMORY_METASPACE);
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
  public void accept(RecordedEvent event) {
    doIfAvailable(
        event,
        "classSpace",
        classSpace -> {
          if (committedSelected && classSpace.hasField(Constants.COMMITTED)) {
            classCommitted = classSpace.getLong(Constants.COMMITTED);
          }
          if (usageSelected && classSpace.hasField(Constants.USED)) {
            classUsage = classSpace.getLong(Constants.USED);
          }
          if (limitSelected && classSpace.hasField(Constants.RESERVED)) {
            classLimit = classSpace.getLong(Constants.RESERVED);
          }
        });

    doIfAvailable(
        event,
        "metaspace",
        metaspace -> {
          if (committedSelected && metaspace.hasField(Constants.COMMITTED)) {
            totalCommitted = metaspace.getLong(Constants.COMMITTED);
          }
          if (usageSelected && metaspace.hasField(Constants.USED)) {
            totalUsage = metaspace.getLong(Constants.USED);
          }
          if (limitSelected && metaspace.hasField(Constants.RESERVED)) {
            totalLimit = metaspace.getLong(Constants.RESERVED);
          }
        });
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

  @Override
  public Optional<Duration> getPollingDuration() {
    return Optional.of(Duration.ofSeconds(1));
  }

  @Override
  public void close() {
    RecordedEventHandler.closeObservables(observables);
  }
}
