/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jdbc;

import static io.opentelemetry.javaagent.instrumentation.jdbc.JdbcUtils.connectionFromStatement;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.tracer.DatabaseClientTracer;
import io.opentelemetry.javaagent.instrumentation.api.CallDepth;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.db.SqlStatementInfo;
import io.opentelemetry.javaagent.instrumentation.api.db.SqlStatementSanitizer;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class JdbcTracer extends DatabaseClientTracer<DbInfo, SqlStatementInfo> {
  private static final JdbcTracer TRACER = new JdbcTracer();

  public static JdbcTracer tracer() {
    return TRACER;
  }

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.javaagent.jdbc";
  }

  @Override
  protected String dbSystem(DbInfo info) {
    return info.getSystem();
  }

  @Override
  protected String dbUser(DbInfo info) {
    return info.getUser();
  }

  @Override
  protected String dbName(DbInfo info) {
    if (info.getName() != null) {
      return info.getName();
    } else {
      return info.getDb();
    }
  }

  // TODO find a way to implement
  @Override
  protected InetSocketAddress peerAddress(DbInfo dbInfo) {
    return null;
  }

  @Override
  protected String dbConnectionString(DbInfo info) {
    return info.getShortUrl();
  }

  public CallDepth getCallDepth() {
    return CallDepthThreadLocalMap.getCallDepth(Statement.class);
  }

  public Context startSpan(Context parentContext, PreparedStatement statement) {
    return startSpan(parentContext, statement, JdbcMaps.preparedStatements.get(statement));
  }

  public Context startSpan(Context parentContext, Statement statement, String query) {
    return startSpan(parentContext, statement, SqlStatementSanitizer.sanitize(query));
  }

  private Context startSpan(
      Context parentContext, Statement statement, SqlStatementInfo queryInfo) {
    Connection connection = connectionFromStatement(statement);
    if (connection == null) {
      return null;
    }

    DbInfo dbInfo = extractDbInfo(connection);

    return startSpan(parentContext, dbInfo, queryInfo);
  }

  @Override
  protected String normalizeQuery(SqlStatementInfo query) {
    return query.getFullStatement();
  }

  @Override
  protected String spanName(DbInfo connection, SqlStatementInfo query, String normalizedQuery) {
    String dbName = dbName(connection);
    if (query.getOperation() == null) {
      return dbName == null ? DB_QUERY : dbName;
    }

    StringBuilder name = new StringBuilder();
    name.append(query.getOperation()).append(' ');
    if (dbName != null) {
      name.append(dbName);
      if (query.getTable() != null) {
        name.append('.');
      }
    }
    if (query.getTable() != null) {
      name.append(query.getTable());
    }
    return name.toString();
  }

  private DbInfo extractDbInfo(Connection connection) {
    DbInfo dbInfo = JdbcMaps.connectionInfo.get(connection);
    /*
     * Logic to get the DBInfo from a JDBC Connection, if the connection was not created via
     * Driver.connect, or it has never seen before, the connectionInfo map will return null and will
     * attempt to extract DBInfo from the connection. If the DBInfo can't be extracted, then the
     * connection will be stored with the DEFAULT DBInfo as the value in the connectionInfo map to
     * avoid retry overhead.
     */
    {
      if (dbInfo == null) {
        try {
          DatabaseMetaData metaData = connection.getMetaData();
          String url = metaData.getURL();
          if (url != null) {
            try {
              dbInfo = JdbcConnectionUrlParser.parse(url, connection.getClientInfo());
            } catch (Throwable ex) {
              // getClientInfo is likely not allowed.
              dbInfo = JdbcConnectionUrlParser.parse(url, null);
            }
          } else {
            dbInfo = DbInfo.DEFAULT;
          }
        } catch (SQLException se) {
          dbInfo = DbInfo.DEFAULT;
        }
        JdbcMaps.connectionInfo.put(connection, dbInfo);
      }
    }
    return dbInfo;
  }
}
