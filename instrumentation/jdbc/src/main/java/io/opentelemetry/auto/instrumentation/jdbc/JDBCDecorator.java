/*
 * Copyright 2020, OpenTelemetry Authors
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

import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.DatabaseClientDecorator;
import io.opentelemetry.auto.bootstrap.instrumentation.jdbc.DBInfo;
import io.opentelemetry.auto.bootstrap.instrumentation.jdbc.JDBCConnectionUrlParser;
import io.opentelemetry.auto.instrumentation.api.Tags;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Tracer;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class JDBCDecorator extends DatabaseClientDecorator<DBInfo> {
  public static final JDBCDecorator DECORATE = new JDBCDecorator();

  public static final Tracer TRACER =
      OpenTelemetry.getTracerProvider().get("io.opentelemetry.auto.jdbc");

  private static final String DB_QUERY = "DB Query";

  @Override
  protected String service() {
    return "jdbc"; // Overridden by onConnection
  }

  @Override
  protected String getComponentName() {
    return "java-jdbc"; // Overridden by onStatement and onPreparedStatement
  }

  @Override
  protected String dbType() {
    return "jdbc";
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

  @Override
  protected String dbUrl(final DBInfo info) {
    return info.getShortUrl();
  }

  public Span onConnection(final Span span, final Connection connection) {
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
            } catch (final Exception ex) {
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

    span.setAttribute(Tags.DB_TYPE, "sql");
    return super.onConnection(span, dbInfo);
  }

  public String spanNameOnStatement(final String statement) {
    return statement == null ? DB_QUERY : statement;
  }

  public String spanNameOnPreparedStatement(final PreparedStatement statement) {
    final String sql = JDBCMaps.preparedStatements.get(statement);
    return sql == null ? DB_QUERY : sql;
  }

  @Override
  public Span onStatement(final Span span, final String statement) {
    span.setAttribute(Tags.COMPONENT, "java-jdbc-statement");
    return super.onStatement(span, statement);
  }

  public Span onPreparedStatement(final Span span, final PreparedStatement statement) {
    final String sql = JDBCMaps.preparedStatements.get(statement);
    span.setAttribute(Tags.COMPONENT, "java-jdbc-prepared_statement");
    return super.onStatement(span, sql);
  }
}
