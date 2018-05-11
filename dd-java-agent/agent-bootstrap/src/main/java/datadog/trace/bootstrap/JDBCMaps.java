package datadog.trace.bootstrap;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import lombok.Data;

/**
 * JDBC instrumentation shares a global map of connection info.
 *
 * <p>In the bootstrap project to ensure visibility by all classes.
 */
public class JDBCMaps {
  public static final Map<Connection, DBInfo> connectionInfo =
      Collections.synchronizedMap(new WeakHashMap<Connection, DBInfo>());
  public static final Map<PreparedStatement, String> preparedStatements =
      Collections.synchronizedMap(new WeakHashMap<PreparedStatement, String>());

  private static final boolean RENAME_UNKNOWN =
      Boolean.valueOf(getPropOrEnv("dd.trace.rename.unknown"));

  public static final String DB_QUERY = RENAME_UNKNOWN ? "DB Query" : "Unknown Query";

  @Data
  public static class DBInfo {
    public static DBInfo DEFAULT =
        new DBInfo("null", RENAME_UNKNOWN ? "database" : "unknown", null);
    private final String url;
    private final String type;
    private final String user;
  }

  private static String getPropOrEnv(final String name) {
    return System.getProperty(name, System.getenv(propToEnvName(name)));
  }

  private static String propToEnvName(final String name) {
    return name.toUpperCase().replace(".", "_");
  }

  /**
   * Utility function to get the DBInfo from a JDBC Connection, if the connection was never seen
   * before, the connectionInfo map will return null and will attempt to extract DBInfo from the
   * connection. If the DBInfo can't be extracted, then the connection will be stored with the
   * UNKNOWN DBInfo as the value in the connectionInfo map to avoid retry overhead.
   *
   * @param connection The JDBC connection
   * @return A DBInfo that contains JDBC connection info
   */
  public static DBInfo getDBInfo(Connection connection) {
    DBInfo dbInfo = connectionInfo.get(connection);
    if (dbInfo == null) {
      try {
        final String url = connection.getMetaData().getURL();
        if (url != null) {
          // Remove end of url to prevent passwords from leaking:
          final String sanitizedURL = url.replaceAll("[?;].*", "");
          final String type = url.split(":", -1)[1];
          String user = connection.getMetaData().getUserName();
          if (user != null && user.trim().equals("")) {
            user = null;
          }
          dbInfo = new JDBCMaps.DBInfo(sanitizedURL, type, user);
        } else {
          dbInfo = DBInfo.DEFAULT;
        }
      } catch (SQLException se) {
        dbInfo = DBInfo.DEFAULT;
      }
      connectionInfo.put(connection, dbInfo);
    }

    return dbInfo;
  }
}
