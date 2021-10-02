/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.instrumentation.api.field.VirtualField;
import java.sql.Connection;
import java.sql.PreparedStatement;

/** Holds info associated with JDBC connections and prepared statements. */
public final class JdbcData {

  public static VirtualField<Connection, DbInfo> connectionInfo =
      VirtualField.find(Connection.class, DbInfo.class);
  public static VirtualField<PreparedStatement, String> preparedStatement =
      VirtualField.find(PreparedStatement.class, String.class);

  private JdbcData() {}
}
