/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.metrics.db;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.ObservableLongUpDownCounter;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import java.util.function.LongSupplier;

/**
 * A helper class that models the <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/metrics/semantic_conventions/database-metrics.md#connection-pools">database
 * client connection pool metrics semantic conventions</a>.
 */
public final class DbConnectionPoolMetrics {

  static final AttributeKey<String> POOL_NAME = stringKey("pool.name");
  static final AttributeKey<String> CONNECTION_STATE = stringKey("state");

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

  public ObservableLongUpDownCounter usedConnections(LongSupplier usedConnectionsGetter) {
    return meter
        .upDownCounterBuilder("db.client.connections.usage")
        .setUnit("connections")
        .setDescription(
            "The number of connections that are currently in state described by the state attribute.")
        .buildWithCallback(
            measurement ->
                measurement.record(usedConnectionsGetter.getAsLong(), usedConnectionsAttributes));
  }

  public ObservableLongUpDownCounter idleConnections(LongSupplier idleConnectionsGetter) {
    return meter
        .upDownCounterBuilder("db.client.connections.usage")
        .setUnit("connections")
        .setDescription(
            "The number of connections that are currently in state described by the state attribute.")
        .buildWithCallback(
            measurement ->
                measurement.record(idleConnectionsGetter.getAsLong(), idleConnectionsAttributes));
  }

  public ObservableLongUpDownCounter minIdleConnections(LongSupplier minIdleConnectionsGetter) {
    return meter
        .upDownCounterBuilder("db.client.connections.idle.min")
        .setUnit("connections")
        .setDescription("The minimum number of idle open connections allowed.")
        .buildWithCallback(
            measurement -> measurement.record(minIdleConnectionsGetter.getAsLong(), attributes));
  }

  public ObservableLongUpDownCounter maxIdleConnections(LongSupplier maxIdleConnectionsGetter) {
    return meter
        .upDownCounterBuilder("db.client.connections.idle.max")
        .setUnit("connections")
        .setDescription("The maximum number of idle open connections allowed.")
        .buildWithCallback(
            measurement -> measurement.record(maxIdleConnectionsGetter.getAsLong(), attributes));
  }

  public ObservableLongUpDownCounter maxConnections(LongSupplier maxConnectionsGetter) {
    return meter
        .upDownCounterBuilder("db.client.connections.max")
        .setUnit("connections")
        .setDescription("The maximum number of open connections allowed.")
        .buildWithCallback(
            measurement -> measurement.record(maxConnectionsGetter.getAsLong(), attributes));
  }

  public ObservableLongUpDownCounter pendingRequestsForConnection(
      LongSupplier pendingRequestsGetter) {
    return meter
        .upDownCounterBuilder("db.client.connections.pending_requests")
        .setUnit("requests")
        .setDescription(
            "The number of pending requests for an open connection, cumulative for the entire pool.")
        .buildWithCallback(
            measurement -> measurement.record(pendingRequestsGetter.getAsLong(), attributes));
  }

  // TODO: should be a BoundLongCounter
  public LongCounter connectionTimeouts() {
    return meter
        .counterBuilder("db.client.connections.timeouts")
        .setUnit("timeouts")
        .setDescription(
            "The number of connection timeouts that have occurred trying to obtain a connection from the pool.")
        .build();
  }

  // TODO: should be a BoundDoubleHistogram
  public DoubleHistogram connectionCreateTime() {
    return meter
        .histogramBuilder("db.client.connections.create_time")
        .setUnit("ms")
        .setDescription("The time it took to create a new connection.")
        .build();
  }

  // TODO: should be a BoundDoubleHistogram
  public DoubleHistogram connectionWaitTime() {
    return meter
        .histogramBuilder("db.client.connections.wait_time")
        .setUnit("ms")
        .setDescription("The time it took to obtain an open connection from the pool.")
        .build();
  }

  // TODO: should be a BoundDoubleHistogram
  public DoubleHistogram connectionUseTime() {
    return meter
        .histogramBuilder("db.client.connections.use_time")
        .setUnit("ms")
        .setDescription("The time between borrowing a connection and returning it to the pool.")
        .build();
  }

  // TODO: should be removed once bound instruments are back
  public Attributes getAttributes() {
    return attributes;
  }
}
