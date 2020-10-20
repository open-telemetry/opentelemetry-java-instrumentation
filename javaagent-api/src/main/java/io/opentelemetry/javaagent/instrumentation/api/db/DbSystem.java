/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.api.db;

public final class DbSystem {

  public static final String CASSANDRA = "cassandra";
  public static final String COUCHBASE = "couchbase";
  public static final String DB2 = "db2";
  public static final String DERBY = "derby";
  public static final String DYNAMODB = "dynamodb";
  public static final String GEODE = "geode";
  public static final String H2 = "h2";
  public static final String HSQLDB = "hsqldb";
  public static final String MARIADB = "mariadb";
  public static final String MONGODB = "mongodb";
  public static final String MSSQL = "mssql";
  public static final String MYSQL = "mysql";
  public static final String ORACLE = "oracle";
  public static final String OTHER_SQL = "other_sql";
  public static final String POSTGRESQL = "postgresql";
  public static final String REDIS = "redis";

  private DbSystem() {}
}
