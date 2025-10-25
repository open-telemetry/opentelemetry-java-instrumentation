/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc;

import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import io.opentelemetry.instrumentation.jdbc.testing.AbstractJdbcInstrumentationTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.extension.RegisterExtension;

class JdbcInstrumentationTest extends AbstractJdbcInstrumentationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static final JdbcTelemetry telemetry =
      JdbcTelemetry.builder(testing.getOpenTelemetry())
          .setDataSourceInstrumenterEnabled(true)
          .setTransactionInstrumenterEnabled(true)
          .build();

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected Connection wrap(Connection connection) throws SQLException {
    return ConnectionWrapper.wrap(connection, telemetry);
  }

  @Override
  protected DataSource wrap(DataSource dataSource) {
    return telemetry.wrap(dataSource);
  }
}
