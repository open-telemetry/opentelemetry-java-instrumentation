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
import jdk.jfr.consumer.RecordedEvent;

/**
 * Handles attributes with pool value CodeCache. This class is internal and is hence not for public
 * use. Its APIs are unstable and can change at any time.
 */
public final class CodeCacheConfigurationHandler implements RecordedEventHandler {
  private static final String EVENT_NAME = "jdk.CodeCacheConfiguration";

  private static final Attributes ATTR =
      Attributes.of(
          Constants.ATTR_MEMORY_TYPE, Constants.NON_HEAP, Constants.ATTR_MEMORY_POOL, "CodeCache");

  private final List<AutoCloseable> observables = new ArrayList<>();

  private volatile long initialSize = 0;

  public CodeCacheConfigurationHandler(Meter meter) {
    observables.add(
        meter
            .upDownCounterBuilder(Constants.METRIC_NAME_MEMORY_INIT)
            .setDescription(Constants.METRIC_DESCRIPTION_MEMORY_INIT)
            .setUnit(Constants.BYTES)
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
    if (event.hasField(Constants.INITIAL_SIZE)) {
      initialSize = event.getLong(Constants.INITIAL_SIZE);
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
