package io.opentelemetry.auto.instrumentation.jdbc;

import static io.opentelemetry.auto.bootstrap.WeakMap.Provider.newWeakMap;

import io.opentelemetry.auto.bootstrap.WeakMap;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * JDBC instrumentation shares a global map of connection info.
 *
 * <p>Should be injected into the bootstrap classpath.
 */
public class JDBCMaps {
  public static final WeakMap<Connection, DBInfo> connectionInfo = newWeakMap();
  public static final WeakMap<PreparedStatement, String> preparedStatements = newWeakMap();
}
