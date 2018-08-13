package datadog.trace.bootstrap;

import static datadog.trace.bootstrap.WeakMap.Provider.newWeakMap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import lombok.Data;

/**
 * JDBC instrumentation shares a global map of connection info.
 *
 * <p>In the bootstrap project to ensure visibility by all classes.
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
