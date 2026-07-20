/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachedbcp.v2_0;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.apachedbcp.v2_0.ApacheDbcpTelemetry;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcConnectionUrlParser;
import io.opentelemetry.javaagent.bootstrap.jdbc.DbInfo;
import org.apache.commons.dbcp2.BasicDataSource;
import org.apache.commons.dbcp2.OpenTelemetryBasicDataSourceUtil;

public class ApacheDbcpSingletons {

  private static final ApacheDbcpTelemetry telemetry =
      ApacheDbcpTelemetry.create(GlobalOpenTelemetry.get());

  public static ApacheDbcpTelemetry telemetry() {
    return telemetry;
  }

  public static String getDataSourceName(BasicDataSource dataSource) {
    DbInfo dbInfo =
        JdbcConnectionUrlParser.parse(
            dataSource.getUrl(),
            OpenTelemetryBasicDataSourceUtil.getConnectionProperties(dataSource));
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

    // Following the semantic conventions, the derived name combines server.address, server.port,
    // and db.namespace, which is not guaranteed to be unique (e.g. multiple pools pointing at the
    // same database). We intentionally do not append a numeric suffix to disambiguate collisions:
    // a per-application sequence number is not stable across restarts or nodes (initialization
    // order can swap suffixes between nodes, and multiple applications on the same node would
    // still collide), so it would not reliably identify which pool the metrics belong to. Since
    // opentelemetry-java 1.50.0, asynchronous instruments spatially aggregate observations that
    // share the same attributes (Sum) instead of dropping them, so a shared pool.name no longer
    // causes lost measurements or duplicate-value warnings.
    return poolName.length() > 0 ? poolName.toString() : "apache-dbcp2";
  }

  private ApacheDbcpSingletons() {}
}
