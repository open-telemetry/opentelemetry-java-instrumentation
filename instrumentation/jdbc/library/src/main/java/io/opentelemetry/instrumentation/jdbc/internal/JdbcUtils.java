/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JdbcUtils {

  private static final Logger logger = LoggerFactory.getLogger(JdbcUtils.class);

  @Nullable private static Field c3poField = null;

  /** Returns the unwrapped connection or null if exception was thrown. */
  public static Connection connectionFromStatement(Statement statement) {
    Connection connection;
    try {
      connection = statement.getConnection();

      if (c3poField != null) {
        if (connection.getClass().getName().equals("com.mchange.v2.c3p0.impl.NewProxyConnection")) {
          return (Connection) c3poField.get(connection);
        }
      }

      try {
        // unwrap the connection to cache the underlying actual connection and to not cache proxy
        // objects
        if (connection.isWrapperFor(Connection.class)) {
          connection = connection.unwrap(Connection.class);
        }
      } catch (Exception | AbstractMethodError e) {
        if (connection != null) {
          // Attempt to work around c3po delegating to an connection that doesn't support
          // unwrapping.
          Class<? extends Connection> connectionClass = connection.getClass();
          if (connectionClass.getName().equals("com.mchange.v2.c3p0.impl.NewProxyConnection")) {
            Field inner = connectionClass.getDeclaredField("inner");
            inner.setAccessible(true);
            c3poField = inner;
            return (Connection) c3poField.get(connection);
          }
        }

        // perhaps wrapping isn't supported?
        // ex: org.h2.jdbc.JdbcConnection v1.3.175
        // or: jdts.jdbc which always throws `AbstractMethodError` (at least up to version 1.3)
        // Stick with original connection.
      }
    } catch (Throwable e) {
      // Had some problem getting the connection.
      logger.debug("Could not get connection for StatementAdvice", e);
      return null;
    }
    return connection;
  }

  public static DbInfo extractDbInfo(Connection connection) {
    // intentionally not using computeIfAbsent() since that would perform computeDbInfo() under a
    // lock, and computeDbInfo() calls back to the application code via Connection.getMetaData()
    // which could then result in a deadlock
    // (e.g. https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/4188)
    DbInfo dbInfo = JdbcData.connectionInfo.get(connection);
    if (dbInfo == null) {
      dbInfo = computeDbInfo(connection);
      JdbcData.connectionInfo.set(connection, JdbcData.intern(dbInfo));
    }
    return dbInfo;
  }

  public static DbInfo computeDbInfo(Connection connection) {
    /*
     * Logic to get the DBInfo from a JDBC Connection, if the connection was not created via
     * Driver.connect, or it has never seen before, the connectionInfo map will return null and will
     * attempt to extract DBInfo from the connection. If the DBInfo can't be extracted, then the
     * connection will be stored with the DEFAULT DBInfo as the value in the connectionInfo map to
     * avoid retry overhead.
     */
    try {
      DatabaseMetaData metaData = connection.getMetaData();
      String url = metaData.getURL();
      if (url != null) {
        try {
          return JdbcConnectionUrlParser.parse(url, connection.getClientInfo());
        } catch (Throwable ex) {
          // getClientInfo is likely not allowed.
          return JdbcConnectionUrlParser.parse(url, null);
        }
      } else {
        return DbInfo.DEFAULT;
      }
    } catch (SQLException se) {
      return DbInfo.DEFAULT;
    }
  }

  private JdbcUtils() {}
}
