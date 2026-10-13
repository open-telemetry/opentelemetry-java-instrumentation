/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.api.metrics.ObservableMeasurement;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.semconv.SchemaUrls;

/**
 * A helper class that models the <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/db/database-metrics.md#connection-pools">database
 * client connection pool metrics semantic conventions</a>.
 */
public final class DbConnectionPoolMetrics {

  static final AttributeKey<String> POOL_NAME = stringKey("db.client.connection.pool.name");
  static final AttributeKey<String> CONNECTION_STATE = stringKey("db.client.connection.state");

  static final String STATE_IDLE = "idle";
  static final String STATE_USED = "used";

  public static DbConnectionPoolMetrics create(
      OpenTelemetry openTelemetry, String instrumentationName, String poolName) {
    return create(openTelemetry, instrumentationName, poolName, Attributes.empty());
  }

  /** Creates database connection pool metrics with additional database attributes. */
  public static DbConnectionPoolMetrics create(
      OpenTelemetry openTelemetry,
      String instrumentationName,
      String poolName,
      Attributes databaseAttributes) {
    MeterBuilder meterBuilder = openTelemetry.getMeterProvider().meterBuilder(instrumentationName);
    String version = EmbeddedInstrumentationProperties.findVersion(instrumentationName);
    if (version != null) {
      meterBuilder.setInstrumentationVersion(version);
    }
    meterBuilder.setSchemaUrl(SchemaUrls.V1_44_0);
    return create(meterBuilder.build(), poolName, databaseAttributes);
  }

  /**
   * Like {@link #create(OpenTelemetry, String, String)}, but accepts a pre-built {@link Meter}.
   *
   * @deprecated Exists only so the {@code tomcat-jdbc-8.5} javaagent can emit the pre-rename {@code
   *     io.opentelemetry.tomcat-jdbc} scope by default; to be removed in 3.0 once v3-preview
   *     becomes the default.
   */
  @Deprecated
  public static DbConnectionPoolMetrics create(Meter meter, String poolName) {
    return create(meter, poolName, Attributes.empty());
  }

  /**
   * Like {@link #create(Meter, String)}, but accepts additional database attributes.
   *
   * @deprecated Exists only so the {@code tomcat-jdbc-8.5} javaagent can emit the pre-rename {@code
   *     io.opentelemetry.tomcat-jdbc} scope by default; to be removed in 3.0 once v3-preview
   *     becomes the default.
   */
  @Deprecated
  public static DbConnectionPoolMetrics create(
      Meter meter, String poolName, Attributes databaseAttributes) {
    AttributesBuilder attributes = Attributes.builder();
    attributes.putAll(databaseAttributes);
    attributes.put(POOL_NAME, poolName);
    return new DbConnectionPoolMetrics(meter, attributes.build());
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
    return meter
        .upDownCounterBuilder("db.client.connection.count")
        .setUnit("{connection}")
        .setDescription(
            "The number of connections that are currently in state described by the state attribute.")
        .buildObserver();
  }

  public ObservableLongMeasurement minIdleConnections() {
    return meter
        .upDownCounterBuilder("db.client.connection.idle.min")
        .setUnit("{connection}")
        .setDescription("The minimum number of idle open connections allowed.")
        .buildObserver();
  }

  public ObservableLongMeasurement maxIdleConnections() {
    return meter
        .upDownCounterBuilder("db.client.connection.idle.max")
        .setUnit("{connection}")
        .setDescription("The maximum number of idle open connections allowed.")
        .buildObserver();
  }

  public ObservableLongMeasurement maxConnections() {
    return meter
        .upDownCounterBuilder("db.client.connection.limit")
        .setUnit("{connection}")
        .setDescription("The maximum number of open connections allowed.")
        .buildObserver();
  }

  public ObservableLongMeasurement pendingRequestsForConnection() {
    return meter
        .upDownCounterBuilder("db.client.connection.pending_requests")
        .setUnit("{request}")
        .setDescription("The number of current pending requests for an open connection.")
        .buildObserver();
  }

  public BatchCallback batchCallback(
      Runnable callback,
      ObservableMeasurement observableMeasurement,
      ObservableMeasurement... additionalMeasurements) {
    return meter.batchCallback(callback, observableMeasurement, additionalMeasurements);
  }

  public LongCounter connectionTimeouts() {
    return meter
        .counterBuilder("db.client.connection.timeouts")
        .setUnit("{timeout}")
        .setDescription(
            "The number of connection timeouts that have occurred trying to obtain a connection from the pool.")
        .build();
  }

  public DoubleHistogram connectionCreateTime() {
    return meter
        .histogramBuilder("db.client.connection.create_time")
        .setUnit("s")
        .setDescription("The time it took to create a new connection.")
        .build();
  }

  public DoubleHistogram connectionWaitTime() {
    return meter
        .histogramBuilder("db.client.connection.wait_time")
        .setUnit("s")
        .setDescription("The time it took to obtain an open connection from the pool.")
        .build();
  }

  public DoubleHistogram connectionUseTime() {
    return meter
        .histogramBuilder("db.client.connection.use_time")
        .setUnit("s")
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
