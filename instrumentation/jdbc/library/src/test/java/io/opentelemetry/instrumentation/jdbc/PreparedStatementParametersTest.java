/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc;

import io.opentelemetry.instrumentation.jdbc.testing.AbstractPreparedStatementParametersTest;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.extension.RegisterExtension;

class PreparedStatementParametersTest extends AbstractPreparedStatementParametersTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static final LibraryJdbcTestTelemetry telemetryHelper =
      new LibraryJdbcTestTelemetry(testing);

  @Override
  protected InstrumentationExtension testing() {
    return testing;
  }

  @Override
  protected Connection wrap(Connection connection) throws SQLException {
    return telemetryHelper.instrumentConnectionWithQueryParameters(connection);
  }
}
