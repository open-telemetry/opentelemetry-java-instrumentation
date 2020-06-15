/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.jdbc;

import static io.opentelemetry.auto.instrumentation.jdbc.JDBCUtils.connectionFromStatement;

import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.DatabaseClientTracer;
import io.opentelemetry.auto.bootstrap.instrumentation.jdbc.DBInfo;
import io.opentelemetry.auto.bootstrap.instrumentation.jdbc.JDBCConnectionUrlParser;
import io.opentelemetry.trace.Span;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;

public class JdbcTracer extends DatabaseClientTracer<DBInfo, String> {
  public static final JdbcTracer TRACER = new JdbcTracer();

  @Override
  protected String getInstrumentationName() {
    return "io.opentelemetry.auto.jdbc";
  }

  @Override
  protected String dbType() {
    return "sql";
  }

  @Override
  protected String dbUser(final DBInfo info) {
    return info.getUser();
  }

  @Override
  protected String dbInstance(final DBInfo info) {
    if (info.getInstance() != null) {
      return info.getInstance();
    } else {
      return info.getDb();
    }
  }

  // TODO find a way to implement
  @Override
  protected InetSocketAddress peerAddress(DBInfo dbInfo) {
    return null;
  }

  @Override
  protected String dbUrl(final DBInfo info) {
    return info.getShortUrl();
  }

  public Span startSpan(Statement statement, String query) {
    final Connection connection = connectionFromStatement(statement);
    if (connection == null) {
      return null;
    }

    final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(Statement.class);
    if (callDepth > 0) {
      return null;
    }
    String originType = statement.getClass().getName();
    DBInfo dbInfo = extractDbInfo(connection);

    return startSpan(dbInfo, query, originType);
  }

  @Override
  public void end(Span span) {
    CallDepthThreadLocalMap.reset(Statement.class);
    super.end(span);
  }

  @Override
  protected String normalizeQuery(String query) {
    return JDBCUtils.normalizeSql(query);
  }

  private DBInfo extractDbInfo(Connection connection) {
    DBInfo dbInfo = JDBCMaps.connectionInfo.get(connection);
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
          final DatabaseMetaData metaData = connection.getMetaData();
          final String url = metaData.getURL();
          if (url != null) {
            try {
              dbInfo = JDBCConnectionUrlParser.parse(url, connection.getClientInfo());
            } catch (final Throwable ex) {
              // getClientInfo is likely not allowed.
              dbInfo = JDBCConnectionUrlParser.parse(url, null);
            }
          } else {
            dbInfo = DBInfo.DEFAULT;
          }
        } catch (final SQLException se) {
          dbInfo = DBInfo.DEFAULT;
        }
        JDBCMaps.connectionInfo.put(connection, dbInfo);
      }
    }
    return dbInfo;
  }
}
