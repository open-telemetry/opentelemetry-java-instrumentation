/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc;

import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetryBuilder;
import io.opentelemetry.instrumentation.jdbc.datasource.internal.Experimental;
import io.opentelemetry.instrumentation.jdbc.testing.AbstractSqlCommenterTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.RegisterExtension;

class SqlCommenterTest extends AbstractSqlCommenterTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected Connection wrap(Connection connection) throws SQLException {
    JdbcTelemetryBuilder builder = JdbcTelemetry.builder(testing.getOpenTelemetry());
    Experimental.setEnableSqlCommenter(builder, true);
    JdbcTelemetry telemetry = builder.build();
    DataSource dataSource = telemetry.wrap(new SingleConnectionDataSource(connection));
    return dataSource.getConnection();
  }
}
