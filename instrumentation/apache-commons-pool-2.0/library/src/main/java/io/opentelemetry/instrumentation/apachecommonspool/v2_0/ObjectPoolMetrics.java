/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachecommonspool.v2_0;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;

final class ObjectPoolMetrics {
  private static final AttributeKey<String> POOL_NAME = stringKey("apache.commons_pool.pool.name");
  private static final AttributeKey<String> OBJECT_STATE =
      stringKey("apache.commons_pool.object.state");

  private static final String STATE_IDLE = "idle";
  private static final String STATE_USED = "used";

  static ObjectPoolMetrics create(
      OpenTelemetry openTelemetry, String instrumentationName, String poolName) {
    MeterBuilder meterBuilder = openTelemetry.getMeterProvider().meterBuilder(instrumentationName);
    String version = EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    if (version != null) {
      meterBuilder.setInstrumentationVersion(version);
    }
    return new ObjectPoolMetrics(meterBuilder.build(), Attributes.of(POOL_NAME, poolName));
  }

  private final Meter meter;
  private final Attributes attributes;
  private final Attributes usedObjectsAttributes;
  private final Attributes idleObjectsAttributes;

  private ObjectPoolMetrics(Meter meter, Attributes attributes) {
    this.meter = meter;
    this.attributes = attributes;
    usedObjectsAttributes = attributes.toBuilder().put(OBJECT_STATE, STATE_USED).build();
    idleObjectsAttributes = attributes.toBuilder().put(OBJECT_STATE, STATE_IDLE).build();
  }

  ObservableLongMeasurement objects() {
    return meter
        .upDownCounterBuilder("apache.commons_pool.object.count")
        .setUnit("{object}")
        .setDescription(
            "The number of objects currently in the state described by the state attribute.")
        .buildObserver();
  }

  ObservableLongMeasurement minIdleObjects() {
    return meter
        .upDownCounterBuilder("apache.commons_pool.object.idle.min")
        .setUnit("{object}")
        .setDescription("The minimum number of idle objects allowed in the pool.")
        .buildObserver();
  }

  ObservableLongMeasurement maxIdleObjects() {
    return meter
        .upDownCounterBuilder("apache.commons_pool.object.idle.max")
        .setUnit("{object}")
        .setDescription("The maximum number of idle objects allowed in the pool.")
        .buildObserver();
  }

  ObservableLongMeasurement maxObjects() {
    return meter
        .upDownCounterBuilder("apache.commons_pool.object.max")
        .setUnit("{object}")
        .setDescription("The maximum number of objects allowed in the pool.")
        .buildObserver();
  }

  ObservableLongMeasurement pendingRequestsForObject() {
    return meter
        .upDownCounterBuilder("apache.commons_pool.request.pending")
        .setUnit("{request}")
        .setDescription("The number of requests currently waiting for an object from the pool.")
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

  Attributes getUsedObjectsAttributes() {
    return usedObjectsAttributes;
  }

  Attributes getIdleObjectsAttributes() {
    return idleObjectsAttributes;
  }
}
