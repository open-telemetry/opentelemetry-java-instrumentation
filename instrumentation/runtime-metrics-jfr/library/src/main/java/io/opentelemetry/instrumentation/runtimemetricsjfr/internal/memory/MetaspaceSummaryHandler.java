/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetricsjfr.internal.memory;

import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.ATTR_POOL;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.ATTR_TYPE;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.COMMITTED;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.METRIC_DESCRIPTION_COMMITTED;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.METRIC_DESCRIPTION_MEMORY;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.METRIC_DESCRIPTION_MEMORY_LIMIT;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.METRIC_NAME_COMMITTED;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.METRIC_NAME_MEMORY;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.METRIC_NAME_MEMORY_LIMIT;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.NON_HEAP;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.RESERVED;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.USED;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimemetricsjfr.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetricsjfr.internal.RecordedEventHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedObject;

/** This class handles GCHeapConfiguration JFR events.
 *
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at any time.
 */
public final class MetaspaceSummaryHandler implements RecordedEventHandler {
  private static final String EVENT_NAME = "jdk.MetaspaceSummary";

  private static final Attributes ATTR_MEMORY_METASPACE =
      Attributes.of(ATTR_TYPE, NON_HEAP, ATTR_POOL, "Metaspace");
  private static final Attributes ATTR_MEMORY_COMPRESSED_CLASS_SPACE =
      Attributes.of(ATTR_TYPE, NON_HEAP, ATTR_POOL, "Compressed Class Space");

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
            .upDownCounterBuilder(METRIC_NAME_MEMORY)
            .setDescription(METRIC_DESCRIPTION_MEMORY)
            .setUnit(BYTES)
            .buildWithCallback(
                measurement -> {
                  measurement.record(classUsage, ATTR_MEMORY_COMPRESSED_CLASS_SPACE);
                  measurement.record(totalUsage, ATTR_MEMORY_METASPACE);
                }));
    observables.add(
        meter
            .upDownCounterBuilder(METRIC_NAME_COMMITTED)
            .setDescription(METRIC_DESCRIPTION_COMMITTED)
            .setUnit(BYTES)
            .buildWithCallback(
                measurement -> {
                  measurement.record(classCommitted, ATTR_MEMORY_COMPRESSED_CLASS_SPACE);
                  measurement.record(totalCommitted, ATTR_MEMORY_METASPACE);
                }));
    observables.add(
        meter
            .upDownCounterBuilder(METRIC_NAME_MEMORY_LIMIT)
            .setDescription(METRIC_DESCRIPTION_MEMORY_LIMIT)
            .setUnit(BYTES)
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
          if (classSpace.hasField(COMMITTED)) {
            classCommitted = classSpace.getLong(COMMITTED);
          }
          if (classSpace.hasField(USED)) {
            classUsage = classSpace.getLong(USED);
          }
          if (classSpace.hasField(RESERVED)) {
            classLimit = classSpace.getLong(RESERVED);
          }
        });

    doIfAvailable(
        event,
        "metaspace",
        metaspace -> {
          if (metaspace.hasField(COMMITTED)) {
            totalCommitted = metaspace.getLong(COMMITTED);
          }
          if (metaspace.hasField(USED)) {
            totalUsage = metaspace.getLong(USED);
          }
          if (metaspace.hasField(RESERVED)) {
            totalLimit = metaspace.getLong(RESERVED);
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
    closeObservables(observables);
  }
}
