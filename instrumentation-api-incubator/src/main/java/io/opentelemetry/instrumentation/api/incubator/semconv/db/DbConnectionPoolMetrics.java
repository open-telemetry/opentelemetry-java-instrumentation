/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;

/**
 * A helper class that models the <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/database/database-metrics.md#connection-pools">database
 * client connection pool metrics semantic conventions</a>.
 */
public final class DbConnectionPoolMetrics {

  static final AttributeKey<String> POOL_NAME =
      stringKey(emitStableDatabaseSemconv() ? "db.client.connection.pool.name" : "pool.name");
  static final AttributeKey<String> CONNECTION_STATE =
      stringKey(emitStableDatabaseSemconv() ? "db.client.connection.state" : "state");

  static final String STATE_IDLE = "idle";
  static final String STATE_USED = "used";

  public static DbConnectionPoolMetrics create(
      OpenTelemetry openTelemetry, String instrumentationName, String poolName) {

    MeterBuilder meterBuilder = openTelemetry.getMeterProvider().meterBuilder(instrumentationName);
    String version = EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    if (version != null) {
      meterBuilder.setInstrumentationVersion(version);
    }
    return new DbConnectionPoolMetrics(meterBuilder.build(), Attributes.of(POOL_NAME, poolName));
  }

  private final Meter meter;
  private final Attributes attributes;
  private final Attributes usedConnectionsAttributes;
  private final Attributes idleConnectionsAttributes;

  DbConnectionPoolMetrics(Meter meter, Attributes attributes) {
    this.meter = meter;
    this.attributes = attributes;
    usedConnectionsAttributes = attributes.toBuilder().put(CONNECTION_STATE, STATE_USED).build();
    idleConnectionsAttributes = attributes.toBuilder().put(CONNECTION_STATE, STATE_IDLE).build();
  }

  public ObservableLongMeasurement connections() {
    String metricName =
        emitStableDatabaseSemconv() ? "db.client.connection.count" : "db.client.connections.usage";
    return meter
        .upDownCounterBuilder(metricName)
        .setUnit(emitStableDatabaseSemconv() ? "{connection}" : "{connections}")
        .setDescription(
            "The number of connections that are currently in state described by the state attribute.")
        .buildObserver();
  }

  public ObservableLongMeasurement minIdleConnections() {
    String metricName =
        emitStableDatabaseSemconv()
            ? "db.client.connection.idle.min"
            : "db.client.connections.idle.min";
    return meter
        .upDownCounterBuilder(metricName)
        .setUnit(emitStableDatabaseSemconv() ? "{connection}" : "{connections}")
        .setDescription("The minimum number of idle open connections allowed.")
        .buildObserver();
  }

  public ObservableLongMeasurement maxIdleConnections() {
    String metricName =
        emitStableDatabaseSemconv()
            ? "db.client.connection.idle.max"
            : "db.client.connections.idle.max";
    return meter
        .upDownCounterBuilder(metricName)
        .setUnit(emitStableDatabaseSemconv() ? "{connection}" : "{connections}")
        .setDescription("The maximum number of idle open connections allowed.")
        .buildObserver();
  }

  public ObservableLongMeasurement maxConnections() {
    String metricName =
        emitStableDatabaseSemconv() ? "db.client.connection.max" : "db.client.connections.max";
    return meter
        .upDownCounterBuilder(metricName)
        .setUnit(emitStableDatabaseSemconv() ? "{connection}" : "{connections}")
        .setDescription("The maximum number of open connections allowed.")
        .buildObserver();
  }

  public ObservableLongMeasurement pendingRequestsForConnection() {
    String metricName =
        emitStableDatabaseSemconv()
            ? "db.client.connection.pending_requests"
            : "db.client.connections.pending_requests";
    return meter
        .upDownCounterBuilder(metricName)
        .setUnit(emitStableDatabaseSemconv() ? "{request}" : "{requests}")
        .setDescription(
            emitStableDatabaseSemconv()
                ? "The number of current pending requests for an open connection."
                : "The number of pending requests for an open connection, cumulative for the entire pool.")
        .buildObserver();
  }

  public BatchCallback batchCallback(
      Runnable callback,
      ObservableMeasurement observableMeasurement,
      ObservableMeasurement... additionalMeasurements) {
    return meter.batchCallback(callback, observableMeasurement, additionalMeasurements);
  }

  public LongCounter connectionTimeouts() {
    String metricName =
        emitStableDatabaseSemconv()
            ? "db.client.connection.timeouts"
            : "db.client.connections.timeouts";
    return meter
        .counterBuilder(metricName)
        .setUnit(emitStableDatabaseSemconv() ? "{timeout}" : "{timeouts}")
        .setDescription(
            "The number of connection timeouts that have occurred trying to obtain a connection from the pool.")
        .build();
  }

  public DoubleHistogram connectionCreateTime() {
    String metricName =
        emitStableDatabaseSemconv()
            ? "db.client.connection.create_time"
            : "db.client.connections.create_time";
    return meter
        .histogramBuilder(metricName)
        .setUnit(emitStableDatabaseSemconv() ? "s" : "ms")
        .setDescription("The time it took to create a new connection.")
        .build();
  }

  public DoubleHistogram connectionWaitTime() {
    String metricName =
        emitStableDatabaseSemconv()
            ? "db.client.connection.wait_time"
            : "db.client.connections.wait_time";
    return meter
        .histogramBuilder(metricName)
        .setUnit(emitStableDatabaseSemconv() ? "s" : "ms")
        .setDescription("The time it took to obtain an open connection from the pool.")
        .build();
  }

  public DoubleHistogram connectionUseTime() {
    String metricName =
        emitStableDatabaseSemconv()
            ? "db.client.connection.use_time"
            : "db.client.connections.use_time";
    return meter
        .histogramBuilder(metricName)
        .setUnit(emitStableDatabaseSemconv() ? "s" : "ms")
        .setDescription("The time between borrowing a connection and returning it to the pool.")
        .build();
  }

  public Attributes getAttributes() {
    return attributes;
  }

  public Attributes getUsedConnectionsAttributes() {
    return usedConnectionsAttributes;
  }

  public Attributes getIdleConnectionsAttributes() {
    return idleConnectionsAttributes;
  }
}
