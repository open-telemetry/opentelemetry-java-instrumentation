/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetricsjfr.internal.memory;

import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.ATTR_POOL;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.ATTR_TYPE;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.INITIAL_SIZE;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.METRIC_DESCRIPTION_MEMORY_INIT;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.METRIC_NAME_MEMORY_INIT;
import static io.opentelemetry.instrumentation.runtimemetricsjfr.internal.Constants.NON_HEAP;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimemetricsjfr.JfrFeature;
import io.opentelemetry.instrumentation.runtimemetricsjfr.internal.RecordedEventHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jdk.jfr.consumer.RecordedEvent;

/** Handles attributes with pool value CodeCache.
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at any time.
 */
public final class CodeCacheConfigurationHandler implements RecordedEventHandler {
  private static final String EVENT_NAME = "jdk.CodeCacheConfiguration";

  private static final Attributes ATTR = Attributes.of(ATTR_TYPE, NON_HEAP, ATTR_POOL, "CodeCache");

  private final List<AutoCloseable> observables = new ArrayList<>();

  private volatile long initialSize = 0;

  public CodeCacheConfigurationHandler(Meter meter) {
    observables.add(
        meter
            .upDownCounterBuilder(METRIC_NAME_MEMORY_INIT)
            .setDescription(METRIC_DESCRIPTION_MEMORY_INIT)
            .setUnit(BYTES)
            .buildWithCallback(measurement -> measurement.record(initialSize, ATTR)));
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
    if (event.hasField(INITIAL_SIZE)) {
      initialSize = event.getLong(INITIAL_SIZE);
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
