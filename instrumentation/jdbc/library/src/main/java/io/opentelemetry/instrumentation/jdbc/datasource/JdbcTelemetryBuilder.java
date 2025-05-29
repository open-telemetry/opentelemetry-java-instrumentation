/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.datasource;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jdbc.internal.DbRequest;
import io.opentelemetry.instrumentation.jdbc.internal.JdbcInstrumenterFactory;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import javax.sql.DataSource;

/** A builder of {@link JdbcTelemetry}. */
public final class JdbcTelemetryBuilder {

  private final OpenTelemetry openTelemetry;
  private boolean dataSourceInstrumenterEnabled = true;
  private boolean statementInstrumenterEnabled = true;
  private boolean statementSanitizationEnabled = true;
  private boolean transactionInstrumenterEnabled = false;
  private boolean captureQueryParameters = false;

  JdbcTelemetryBuilder(OpenTelemetry openTelemetry) {
    this.openTelemetry = openTelemetry;
  }

  /** Configures whether spans are created for JDBC Connections. Enabled by default. */
  @CanIgnoreReturnValue
  public JdbcTelemetryBuilder setDataSourceInstrumenterEnabled(boolean enabled) {
    this.dataSourceInstrumenterEnabled = enabled;
    return this;
  }

  /** Configures whether spans are created for JDBC Statements. Enabled by default. */
  @CanIgnoreReturnValue
  public JdbcTelemetryBuilder setStatementInstrumenterEnabled(boolean enabled) {
    this.statementInstrumenterEnabled = enabled;
    return this;
  }

  /** Configures whether JDBC Statements are sanitized. Enabled by default. */
  @CanIgnoreReturnValue
  public JdbcTelemetryBuilder setStatementSanitizationEnabled(boolean enabled) {
    this.statementSanitizationEnabled = enabled;
    return this;
  }

  /** Configures whether spans are created for JDBC Transactions. Disabled by default. */
  @CanIgnoreReturnValue
  public JdbcTelemetryBuilder setTransactionInstrumenterEnabled(boolean enabled) {
    this.transactionInstrumenterEnabled = enabled;
    return this;
  }

  /**
   * Configures whether parameters are captured for JDBC Statements. Enabling this option disables
   * the statement sanitization. Disabled by default.
   *
   * <p>WARNING: captured query parameters may contain sensitive information such as passwords,
   * personally identifiable information or protected health info.
   */
  @CanIgnoreReturnValue
  public JdbcTelemetryBuilder setCaptureQueryParameters(boolean enabled) {
    this.captureQueryParameters = enabled;
    return this;
  }

  /** Returns a new {@link JdbcTelemetry} with the settings of this {@link JdbcTelemetryBuilder}. */
  public JdbcTelemetry build() {
    Instrumenter<DataSource, DbInfo> dataSourceInstrumenter =
        JdbcInstrumenterFactory.createDataSourceInstrumenter(
            openTelemetry, dataSourceInstrumenterEnabled);
    Instrumenter<DbRequest, Void> statementInstrumenter =
        JdbcInstrumenterFactory.createStatementInstrumenter(
            openTelemetry,
            statementInstrumenterEnabled,
            statementSanitizationEnabled,
            captureQueryParameters);
    Instrumenter<DbRequest, Void> transactionInstrumenter =
        JdbcInstrumenterFactory.createTransactionInstrumenter(
            openTelemetry, transactionInstrumenterEnabled);

    return new JdbcTelemetry(
        dataSourceInstrumenter,
        statementInstrumenter,
        transactionInstrumenter,
        captureQueryParameters);
  }
}
