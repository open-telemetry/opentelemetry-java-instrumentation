/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.jdbc.v8_5;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.BatchCallback;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.MeterBuilder;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.DbConnectionPoolMetrics;
import io.opentelemetry.instrumentation.api.internal.EmbeddedInstrumentationProperties;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tomcat.jdbc.pool.DataSourceProxy;

public class TomcatConnectionPoolMetrics {

  private static final OpenTelemetry openTelemetry = GlobalOpenTelemetry.get();
  // version file is generated from the gradle module name; look it up explicitly so the legacy
  // scope name still resolves to a version
  private static final String VERSION_LOOKUP_NAME = "io.opentelemetry.tomcat-jdbc-8.5";
  private static final String INSTRUMENTATION_NAME =
      AgentCommonConfig.get().isV3Preview()
          ? VERSION_LOOKUP_NAME
          // keep the pre-rename scope name so existing dashboards/filters on
          // otel.scope.name="io.opentelemetry.tomcat-jdbc" continue to work
          : "io.opentelemetry.tomcat-jdbc";
  private static final Meter meter = buildMeter();

  // a weak map does not make sense here because each Meter holds a reference to the dataSource
  // DataSourceProxy does not implement equals()/hashCode(), so it's safe to keep them in a plain
  // ConcurrentHashMap
  private static final Map<DataSourceProxy, BatchCallback> dataSourceMetrics =
      new ConcurrentHashMap<>();

  public static void registerMetrics(DataSourceProxy dataSource) {
    dataSourceMetrics.computeIfAbsent(dataSource, TomcatConnectionPoolMetrics::createInstruments);
  }

  // deprecated DbConnectionPoolMetrics.create(Meter, String) overload exists solely so we can keep
  // emitting the legacy io.opentelemetry.tomcat-jdbc scope by default; goes away in 3.0 once
  // v3-preview becomes default
  @SuppressWarnings("deprecation")
  private static BatchCallback createInstruments(DataSourceProxy dataSource) {
    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(meter, dataSource.getPoolName());

    ObservableLongMeasurement connections = metrics.connections();
    ObservableLongMeasurement minIdleConnections = metrics.minIdleConnections();
    ObservableLongMeasurement maxIdleConnections = metrics.maxIdleConnections();
    ObservableLongMeasurement maxConnections = metrics.maxConnections();
    ObservableLongMeasurement pendingRequestsForConnection = metrics.pendingRequestsForConnection();

    Attributes attributes = metrics.getAttributes();
    Attributes usedConnectionsAttributes = metrics.getUsedConnectionsAttributes();
    Attributes idleConnectionsAttributes = metrics.getIdleConnectionsAttributes();

    return metrics.batchCallback(
        () -> {
          connections.record(dataSource.getActive(), usedConnectionsAttributes);
          connections.record(dataSource.getIdle(), idleConnectionsAttributes);
          minIdleConnections.record(dataSource.getMinIdle(), attributes);
          maxIdleConnections.record(dataSource.getMaxIdle(), attributes);
          maxConnections.record(dataSource.getMaxActive(), attributes);
          pendingRequestsForConnection.record(dataSource.getWaitCount(), attributes);
        },
        connections,
        minIdleConnections,
        maxIdleConnections,
        maxConnections,
        pendingRequestsForConnection);
  }

  public static void unregisterMetrics(DataSourceProxy dataSource) {
    BatchCallback callback = dataSourceMetrics.remove(dataSource);
    if (callback != null) {
      callback.close();
    }
  }

  private static Meter buildMeter() {
    MeterBuilder meterBuilder = openTelemetry.getMeterProvider().meterBuilder(INSTRUMENTATION_NAME);
    String version = EmbeddedInstrumentationProperties.findVersion(VERSION_LOOKUP_NAME);
    if (version != null) {
      meterBuilder.setInstrumentationVersion(version);
    }
    return meterBuilder.build();
  }

  private TomcatConnectionPoolMetrics() {}
}
