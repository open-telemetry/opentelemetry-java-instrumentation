/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.sql;

import io.vertx.sqlclient.SqlConnectOptions;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

public final class VertxSqlClientRequest {
  // copied from DbAttributes.DbSystemNameValues
  private static final String POSTGRESQL = "postgresql";
  // copied from DbAttributes.DbSystemNameValues
  private static final String MYSQL = "mysql";
  // copied from DbAttributes.DbSystemNameValues
  private static final String MICROSOFT_SQL_SERVER = "microsoft.sql_server";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String ORACLE_DB = "oracle.db";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String DB2 = "db2";
  // copied from DbIncubatingAttributes.DbSystemNameIncubatingValues
  private static final String OTHER_SQL = "other_sql";

  private static final Map<String, String> DB_SYSTEM_BY_CLASS_NAME = buildDbSystemMap();

  private final String queryText;
  private final SqlConnectOptions sqlConnectOptions;
  private final boolean parameterizedQuery;

  public VertxSqlClientRequest(
      String queryText, SqlConnectOptions sqlConnectOptions, boolean parameterizedQuery) {
    this.queryText = queryText;
    this.sqlConnectOptions = sqlConnectOptions;
    this.parameterizedQuery = parameterizedQuery;
  }

  public String getQueryText() {
    return queryText;
  }

  public String getUser() {
    return sqlConnectOptions != null ? sqlConnectOptions.getUser() : null;
  }

  public String getDatabase() {
    return sqlConnectOptions != null ? sqlConnectOptions.getDatabase() : null;
  }

  public String getHost() {
    return sqlConnectOptions != null ? sqlConnectOptions.getHost() : null;
  }

  public Integer getPort() {
    return sqlConnectOptions != null ? sqlConnectOptions.getPort() : null;
  }

  public boolean isParameterizedQuery() {
    return parameterizedQuery;
  }

  @Nullable
  public String getDbSystemName() {
    if (sqlConnectOptions == null) {
      return null;
    }
    // First check if the db system was resolved from the Pool class at pool creation time
    String dbSystem = VertxSqlClientUtil.getConnectOptionsDbSystem(sqlConnectOptions);
    if (dbSystem != null) {
      return dbSystem;
    }
    // Fall back to checking the SqlConnectOptions class hierarchy
    Class<?> clazz = sqlConnectOptions.getClass();
    while (clazz != null) {
      dbSystem = DB_SYSTEM_BY_CLASS_NAME.get(clazz.getName());
      if (dbSystem != null) {
        return dbSystem;
      }
      clazz = clazz.getSuperclass();
    }
    return OTHER_SQL;
  }

  private static Map<String, String> buildDbSystemMap() {
    Map<String, String> map = new HashMap<>();
    map.put("io.vertx.pgclient.PgConnectOptions", POSTGRESQL);
    map.put("io.vertx.mysqlclient.MySQLConnectOptions", MYSQL);
    map.put("io.vertx.mssqlclient.MSSQLConnectOptions", MICROSOFT_SQL_SERVER);
    map.put("io.vertx.oracleclient.OracleConnectOptions", ORACLE_DB);
    map.put("io.vertx.db2client.DB2ConnectOptions", DB2);
    return map;
  }
}
