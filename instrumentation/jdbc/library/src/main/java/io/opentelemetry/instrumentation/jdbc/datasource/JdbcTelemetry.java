/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.datasource;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import javax.sql.DataSource;

/** Entrypoint for instrumenting a JDBC DataSources. */
public final class JdbcTelemetry {

  /** Returns a new {@link JdbcTelemetry} configured with the given {@link OpenTelemetry}. */
  public static JdbcTelemetry create(OpenTelemetry openTelemetry) {
    return builder(openTelemetry).build();
  }

  /** Returns a new {@link JdbcTelemetryBuilder} configured with the given {@link OpenTelemetry}. */
  public static JdbcTelemetryBuilder builder(OpenTelemetry openTelemetry) {
    return new JdbcTelemetryBuilder(openTelemetry);
  }

  private final Instrumenter<DataSource, DbInfo> dataSourceInstrumenter;
  private final Instrumenter<DbRequest, Void> statementInstrumenter;

  JdbcTelemetry(
      Instrumenter<DataSource, DbInfo> dataSourceInstrumenter,
      Instrumenter<DbRequest, Void> statementInstrumenter) {
    this.dataSourceInstrumenter = dataSourceInstrumenter;
    this.statementInstrumenter = statementInstrumenter;
  }

  public DataSource wrap(DataSource dataSource) {
    return new OpenTelemetryDataSource(
        dataSource, this.dataSourceInstrumenter, this.statementInstrumenter);
  }
}
