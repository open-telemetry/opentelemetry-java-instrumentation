/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc.test;

import io.opentelemetry.instrumentation.jdbc.TestConnection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

class DbCallingConnection extends TestConnection {
  final boolean usePreparedStatement;

  DbCallingConnection(boolean usePreparedStatement) {
    super(false);
    this.usePreparedStatement = usePreparedStatement;
  }

  @Override
  public DatabaseMetaData getMetaData() throws SQLException {
    // simulate retrieving DB metadata from the DB itself
    if (usePreparedStatement) {
      prepareStatement("SELECT * from DB_METADATA").executeQuery();
    } else {
      createStatement().executeQuery("SELECT * from DB_METADATA");
    }
    return super.getMetaData();
  }
}
