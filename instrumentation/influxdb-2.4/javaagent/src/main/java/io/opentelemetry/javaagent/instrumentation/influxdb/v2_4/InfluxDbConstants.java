/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.influxdb.v2_4;

final class InfluxDbConstants {

  private InfluxDbConstants() {}

  public static final String CREATE_DATABASE_STATEMENT_NEW = "CREATE DATABASE \"%s\"";

  /** In influxDB 0.x version, it uses below statement format to create a database. */
  public static final String CREATE_DATABASE_STATEMENT_OLD = "CREATE DATABASE IF NOT EXISTS \"%s\"";

  public static final String DELETE_DATABASE_STATEMENT = "DROP DATABASE \"%s\"";
}
