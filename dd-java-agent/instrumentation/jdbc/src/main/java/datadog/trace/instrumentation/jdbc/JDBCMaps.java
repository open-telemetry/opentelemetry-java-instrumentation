package datadog.trace.instrumentation.jdbc;

import static datadog.trace.bootstrap.WeakMap.Provider.newWeakMap;

import datadog.trace.bootstrap.WeakMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import lombok.Data;

/**
 * JDBC instrumentation shares a global map of connection info.
 *
 * <p>Should be injected into the bootstrap classpath.
 */
public class JDBCMaps {
  public static final WeakMap<Connection, DBInfo> connectionInfo = newWeakMap();
  public static final WeakMap<PreparedStatement, String> preparedStatements = newWeakMap();

  public static final String DB_QUERY = "DB Query";

  @Data
  public static class DBInfo {
    public static DBInfo DEFAULT = new DBInfo("null", "database", null);
    private final String url;
    private final String type;
    private final String user;
  }
}
