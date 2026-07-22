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
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.javaagent.bootstrap.internal.AgentCommonConfig;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tomcat.jdbc.pool.DataSourceProxy;
import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.apache.tomcat.jdbc.pool.PoolProperties;

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
  private static final String DEFAULT_POOL_NAME = "tomcat-jdbc";
  private static final String TOMCAT_DEFAULT_POOL_NAME_PREFIX = "Tomcat Connection Pool[";
  private static final String TOMCAT_DEFAULT_POOL_NAME_SUFFIX =
      "-" + System.identityHashCode(PoolProperties.class) + "]";
  private static final Meter meter = buildMeter();

  // a weak map does not make sense here because each Meter holds a reference to the dataSource
  // DataSourceProxy does not implement equals()/hashCode(), so it's safe to keep them in a plain
  // ConcurrentHashMap
  private static final Map<DataSourceProxy, BatchCallback> dataSourceMetrics =
      new ConcurrentHashMap<>();

  public static void registerMetrics(DataSourceProxy dataSource) {
    dataSourceMetrics.computeIfAbsent(dataSource, TomcatConnectionPoolMetrics::createInstruments);
  }

  @SuppressWarnings("deprecation") // deprecated overload keeps the legacy scope by default
  private static BatchCallback createInstruments(DataSourceProxy dataSource) {
    DbConnectionPoolMetrics metrics =
        DbConnectionPoolMetrics.create(meter, getPoolName(dataSource));

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

  private static String getPoolName(DataSourceProxy dataSource) {
    String configuredPoolName = dataSource.getPoolName();
    if (configuredPoolName != null && !isDefaultTomcatPoolName(configuredPoolName)) {
      return configuredPoolName;
    }

    PoolConfiguration poolProperties = dataSource.getPoolProperties();
    DbInfo dbInfo =
        JdbcConnectionUrlParser.parse(poolProperties.getUrl(), poolProperties.getDbProperties());
    String serverAddress = dbInfo.getServerAddress();
    Integer serverPort = dbInfo.getServerPort();
    String dbNamespace = dbInfo.getDbNamespace();

    StringBuilder poolName = new StringBuilder();
    if (serverAddress != null) {
      poolName.append(serverAddress);
      if (serverPort != null) {
        poolName.append(':').append(serverPort);
      }
    }
    if (dbNamespace != null) {
      if (poolName.length() > 0) {
        poolName.append('/');
      }
      poolName.append(dbNamespace);
    }

    // The derived name is intentionally not made unique with a numeric suffix: sequence numbers
    // are unstable across restarts and initialization order. Asynchronous metric observations with
    // equal attributes are aggregated, so multiple pools for the same database can share this pool
    // name.
    return poolName.length() > 0 ? poolName.toString() : DEFAULT_POOL_NAME;
  }

  private static boolean isDefaultTomcatPoolName(String poolName) {
    if (!poolName.startsWith(TOMCAT_DEFAULT_POOL_NAME_PREFIX)
        || !poolName.endsWith(TOMCAT_DEFAULT_POOL_NAME_SUFFIX)) {
      return false;
    }

    int counterStart = TOMCAT_DEFAULT_POOL_NAME_PREFIX.length();
    int counterEnd = poolName.length() - TOMCAT_DEFAULT_POOL_NAME_SUFFIX.length();
    if (counterStart == counterEnd) {
      return false;
    }

    for (int index = counterStart; index < counterEnd; index++) {
      char character = poolName.charAt(index);
      if (character < '0' || character > '9') {
        return false;
      }
    }
    return true;
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
