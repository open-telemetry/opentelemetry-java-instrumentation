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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
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
      Attributes.of(
          Constants.ATTR_MEMORY_TYPE, Constants.NON_HEAP, Constants.ATTR_MEMORY_POOL, "Metaspace");
  private static final Attributes ATTR_MEMORY_COMPRESSED_CLASS_SPACE =
      Attributes.of(
          Constants.ATTR_MEMORY_TYPE,
          Constants.NON_HEAP,
          Constants.ATTR_MEMORY_POOL,
          "Compressed Class Space");

  private final List<AutoCloseable> observables = new ArrayList<>();

  private volatile long classUsage = 0;
  private volatile long classCommitted = 0;
  private volatile long totalUsage = 0;
  private volatile long totalCommitted = 0;
  private volatile long classLimit = 0;
  private volatile long totalLimit = 0;

  public MetaspaceSummaryHandler(Meter meter) {
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

  @Override
  public String getEventName() {
    return EVENT_NAME;
  }

  @Override
  public JfrFeature getFeature() {
    return JfrFeature.MEMORY_POOL_METRICS;
  }

  @Override
  public void accept(RecordedEvent event) {
    doIfAvailable(
        event,
        "classSpace",
        classSpace -> {
          if (classSpace.hasField(Constants.COMMITTED)) {
            classCommitted = classSpace.getLong(Constants.COMMITTED);
          }
          if (classSpace.hasField(Constants.USED)) {
            classUsage = classSpace.getLong(Constants.USED);
          }
          if (classSpace.hasField(Constants.RESERVED)) {
            classLimit = classSpace.getLong(Constants.RESERVED);
          }
        });

    doIfAvailable(
        event,
        "metaspace",
        metaspace -> {
          if (metaspace.hasField(Constants.COMMITTED)) {
            totalCommitted = metaspace.getLong(Constants.COMMITTED);
          }
          if (metaspace.hasField(Constants.USED)) {
            totalUsage = metaspace.getLong(Constants.USED);
          }
          if (metaspace.hasField(Constants.RESERVED)) {
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
    if (value instanceof RecordedObject) {
      closure.accept((RecordedObject) value);
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
