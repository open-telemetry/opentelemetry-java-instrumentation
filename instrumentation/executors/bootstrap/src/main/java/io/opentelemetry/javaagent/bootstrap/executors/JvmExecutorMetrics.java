/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.executors;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;

public final class JvmExecutorMetrics {

  private static final AttributeKey<String> EXECUTOR_NAME = stringKey("jvm.executor.name");
  private static final AttributeKey<String> EXECUTOR_OWNER_NAME_ATTRIBUTE =
      stringKey("jvm.executor.owner.name");
  private static final AttributeKey<String> EXECUTOR_TYPE = stringKey("jvm.executor.type");
  private static final AttributeKey<String> STATE = stringKey("jvm.executor.state");

  private static final String ACTIVE_STATE = "active";
  private static final String IDLE_STATE = "idle";

  static JvmExecutorMetrics create(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      String executorName,
      String executorOwnerName,
      String executorType) {
    MeterBuilder meterBuilder = openTelemetry.getMeterProvider().meterBuilder(instrumentationName);
    String instrumentationVersion =
        EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    if (instrumentationVersion != null) {
      meterBuilder.setInstrumentationVersion(instrumentationVersion);
    }

    return new JvmExecutorMetrics(
        meterBuilder.build(),
        Attributes.of(
            EXECUTOR_NAME,
            executorName,
            EXECUTOR_OWNER_NAME_ATTRIBUTE,
            executorOwnerName,
            EXECUTOR_TYPE,
            executorType));
  }

  private final Meter meter;
  private final Attributes attributes;
  private final Attributes activeThreadAttributes;
  private final Attributes idleThreadAttributes;

  private JvmExecutorMetrics(Meter meter, Attributes attributes) {
    this.meter = meter;
    this.attributes = attributes;
    activeThreadAttributes = attributes.toBuilder().put(STATE, ACTIVE_STATE).build();
    idleThreadAttributes = attributes.toBuilder().put(STATE, IDLE_STATE).build();
  }

  ObservableLongMeasurement threadCount() {
    return meter
        .upDownCounterBuilder("jvm.executor.thread.count")
        .setUnit("{thread}")
        .setDescription("The number of executor threads that are currently in the described state.")
        .buildObserver();
  }

  ObservableLongMeasurement coreThreads() {
    return meter
        .upDownCounterBuilder("jvm.executor.thread.core")
        .setUnit("{thread}")
        .setDescription("The core number of threads configured for the executor.")
        .buildObserver();
  }

  ObservableLongMeasurement maxThreads() {
    return meter
        .upDownCounterBuilder("jvm.executor.thread.max")
        .setUnit("{thread}")
        .setDescription("The maximum number of threads allowed for the executor.")
        .buildObserver();
  }

  ObservableLongMeasurement queueSize() {
    return meter
        .upDownCounterBuilder("jvm.executor.queue.size")
        .setUnit("{task}")
        .setDescription("The number of tasks currently queued for execution.")
        .buildObserver();
  }

  ObservableLongMeasurement queueRemaining() {
    return meter
        .upDownCounterBuilder("jvm.executor.queue.remaining")
        .setUnit("{task}")
        .setDescription("The remaining task capacity of the executor queue.")
        .buildObserver();
  }

  ObservableLongMeasurement completedTasks() {
    return meter
        .counterBuilder("jvm.executor.task.completed")
        .setUnit("{task}")
        .setDescription("The number of tasks completed by the executor.")
        .buildObserver();
  }

  LongCounter rejectedTasks() {
    return meter
        .counterBuilder("jvm.executor.task.rejected")
        .setUnit("{task}")
        .setDescription("The number of tasks rejected by the executor.")
        .build();
  }

  BatchCallback batchCallback(
      Runnable callback,
      ObservableMeasurement observableMeasurement,
      ObservableMeasurement... additionalMeasurements) {
    return meter.batchCallback(callback, observableMeasurement, additionalMeasurements);
  }

  Attributes attributes() {
    return attributes;
  }

  Attributes activeThreadAttributes() {
    return activeThreadAttributes;
  }

  Attributes idleThreadAttributes() {
    return idleThreadAttributes;
  }
}
