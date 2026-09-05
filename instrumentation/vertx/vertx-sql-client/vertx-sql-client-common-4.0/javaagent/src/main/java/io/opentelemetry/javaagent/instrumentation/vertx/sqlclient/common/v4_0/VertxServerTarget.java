/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sqlclient.common.v4_0;

import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MICROSOFT_SQL_SERVER;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.MYSQL;
import static io.opentelemetry.semconv.DbAttributes.DbSystemNameValues.POSTGRESQL;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.IBM_DB2;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.ORACLE_DB;

import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTarget;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.DbServerTargetBuilder;
import io.vertx.sqlclient.SqlConnectOptions;
import java.util.List;
import javax.annotation.Nullable;

public final class VertxServerTarget {

  @Nullable
  public static DbServerTarget from(
      @Nullable SqlConnectOptions connectOptions, String dbSystemName) {
    if (connectOptions == null) {
      return null;
    }
    String host = connectOptions.getHost();
    if (host != null && host.startsWith("/")) {
      return DbServerTarget.unixSocket(host);
    }
    return addEndpoint(DbServerTarget.builder(defaultPort(dbSystemName)), connectOptions).build();
  }

  @Nullable
  public static DbServerTarget from(
      @Nullable List<? extends SqlConnectOptions> connectOptions, String dbSystemName) {
    if (connectOptions == null || connectOptions.isEmpty()) {
      return null;
    }
    if (connectOptions.size() == 1) {
      return from(connectOptions.get(0), dbSystemName);
    }

    DbServerTargetBuilder builder = DbServerTarget.builder(defaultPort(dbSystemName));
    for (SqlConnectOptions options : connectOptions) {
      String host = options != null ? options.getHost() : null;
      if (host != null && host.startsWith("/")) {
        return null;
      }
      addEndpoint(builder, options);
    }
    return builder.build();
  }

  private static DbServerTargetBuilder addEndpoint(
      DbServerTargetBuilder builder, @Nullable SqlConnectOptions connectOptions) {
    if (connectOptions == null) {
      return builder.addEndpoint(null, -1);
    }
    return builder.addEndpoint(connectOptions.getHost(), connectOptions.getPort());
  }

  private static int defaultPort(String dbSystemName) {
    if (POSTGRESQL.equals(dbSystemName)) {
      return 5432;
    }
    if (MYSQL.equals(dbSystemName)) {
      return 3306;
    }
    if (MICROSOFT_SQL_SERVER.equals(dbSystemName)) {
      return 1433;
    }
    if (ORACLE_DB.equals(dbSystemName)) {
      return 1521;
    }
    if (IBM_DB2.equals(dbSystemName)) {
      return 50000;
    }
    return -1;
  }

  private VertxServerTarget() {}
}
