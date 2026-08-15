/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.executors.metrics;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import javax.annotation.Nullable;

final class JvmExecutorMetrics {

  private static final AttributeKey<String> EXECUTOR_NAME = stringKey("jvm.executor.name");
  private static final AttributeKey<String> EXECUTOR_OWNER_NAME =
      stringKey("jvm.executor.owner.name");
  private static final AttributeKey<String> EXECUTOR_TYPE = stringKey("jvm.executor.type");
  private static final AttributeKey<String> THREAD_STATE = stringKey("jvm.executor.thread.state");

  private static final String ACTIVE_STATE = "active";
  private static final String IDLE_STATE = "idle";

  private final Meter meter;
  private final Attributes attributes;
  private final Attributes activeThreadAttributes;
  private final Attributes idleThreadAttributes;

  static JvmExecutorMetrics create(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      String executorName,
      @Nullable String executorOwnerName,
      String executorType) {
    MeterBuilder meterBuilder = openTelemetry.getMeterProvider().meterBuilder(instrumentationName);
    String instrumentationVersion =
        EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    if (instrumentationVersion != null) {
      meterBuilder.setInstrumentationVersion(instrumentationVersion);
    }

    AttributesBuilder attributes =
        Attributes.builder()
            .put(EXECUTOR_NAME, executorName)
            .put(EXECUTOR_TYPE, executorType)
            .put(EXECUTOR_OWNER_NAME, executorOwnerName);

    return new JvmExecutorMetrics(meterBuilder.build(), attributes.build());
  }

  private JvmExecutorMetrics(Meter meter, Attributes attributes) {
    this.meter = meter;
    this.attributes = attributes;
    activeThreadAttributes = attributes.toBuilder().put(THREAD_STATE, ACTIVE_STATE).build();
    idleThreadAttributes = attributes.toBuilder().put(THREAD_STATE, IDLE_STATE).build();
  }

  ObservableLongMeasurement threadCount() {
    return meter
        .upDownCounterBuilder("jvm.executor.thread.count")
        .setUnit("{thread}")
        .setDescription(
            "The number of executor threads that are currently in the state described by the jvm.executor.thread.state attribute.")
        .buildObserver();
  }

  ObservableLongMeasurement coreThreads() {
    return meter
        .upDownCounterBuilder("jvm.executor.thread.core")
        .setUnit("{thread}")
        .setDescription("The number of core threads configured for the executor.")
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

  ObservableLongMeasurement queueCapacity() {
    return meter
        .upDownCounterBuilder("jvm.executor.queue.capacity")
        .setUnit("{task}")
        .setDescription("The maximum number of tasks the executor queue can hold.")
        .buildObserver();
  }

  ObservableLongMeasurement completedTasks() {
    return meter
        .counterBuilder("jvm.executor.task.completed")
        .setUnit("{task}")
        .setDescription("The number of tasks completed by the executor.")
        .buildObserver();
  }

  ObservableLongMeasurement rejectedTasks() {
    return meter
        .counterBuilder("jvm.executor.task.rejected")
        .setUnit("{task}")
        .setDescription("The number of tasks rejected by the executor.")
        .buildObserver();
  }

  BatchCallback batchCallback(
      Runnable callback,
      ObservableMeasurement observableMeasurement,
      ObservableMeasurement... additionalMeasurements) {
    return meter.batchCallback(callback, observableMeasurement, additionalMeasurements);
  }

  Attributes getAttributes() {
    return attributes;
  }

  Attributes getActiveThreadAttributes() {
    return activeThreadAttributes;
  }

  Attributes getIdleThreadAttributes() {
    return idleThreadAttributes;
  }
}
