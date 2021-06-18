/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

// Includes work from:
/*
 * Copyright 2017-2021 The OpenTracing Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package io.opentelemetry.instrumentation.jdbc;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.instrumentation.jdbc.parser.URLParser;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Logger;
import javax.sql.DataSource;

public class TracingDataSource implements DataSource, AutoCloseable {

  private static final boolean DEFAULT_WITH_ACTIVE_SPAN_ONLY = false;
  private static final Set<String> DEFAULT_IGNORED_STATEMENTS = Collections.emptySet();

  private final Tracer tracer;
  private final DataSource underlying;
  private final ConnectionInfo connectionInfo;
  private final boolean withActiveSpanOnly;
  private final Set<String> ignoreStatements;

  public TracingDataSource(final Tracer tracer, final DataSource underlying) {
    this(tracer, underlying, null, DEFAULT_WITH_ACTIVE_SPAN_ONLY, DEFAULT_IGNORED_STATEMENTS);
  }

  public TracingDataSource(
      final Tracer tracer,
      final DataSource underlying,
      final ConnectionInfo connectionInfo,
      final boolean withActiveSpanOnly,
      final Set<String> ignoreStatements) {
    this.tracer = tracer;
    this.underlying = underlying;
    ConnectionInfo info = connectionInfo;
    if (info == null) {
      try {
        Method method;
        try {
          method = underlying.getClass().getMethod("getJdbcUrl");
        } catch (NoSuchMethodException e) {
          method = underlying.getClass().getMethod("getUrl");
        }
        info = URLParser.parse((String) method.invoke(underlying));
      } catch (Exception ignored) {
        info = ConnectionInfo.UNKNOWN_CONNECTION_INFO;
      }
    }
    this.connectionInfo = info;
    this.withActiveSpanOnly = withActiveSpanOnly;
    this.ignoreStatements = ignoreStatements;
  }

  public DataSource getUnderlying() {
    return underlying;
  }

  @Override
  public Connection getConnection() throws SQLException {
    final Connection connection =
        JdbcTracingUtils.call(
            "AcquireConnection",
            underlying::getConnection,
            null,
            connectionInfo,
            withActiveSpanOnly,
            null,
            tracer);

    return WrapperProxy.wrap(
        connection,
        new TracingConnection(
            connection, connectionInfo, withActiveSpanOnly, ignoreStatements, tracer));
  }

  @Override
  public Connection getConnection(final String username, final String password)
      throws SQLException {
    final Connection connection =
        JdbcTracingUtils.call(
            "AcquireConnection",
            () -> underlying.getConnection(username, password),
            null,
            connectionInfo,
            withActiveSpanOnly,
            null,
            tracer);

    return WrapperProxy.wrap(
        connection,
        new TracingConnection(
            connection, connectionInfo, withActiveSpanOnly, ignoreStatements, tracer));
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return underlying.getLogWriter();
  }

  @Override
  public void setLogWriter(final PrintWriter out) throws SQLException {
    underlying.setLogWriter(out);
  }

  @Override
  public void setLoginTimeout(final int seconds) throws SQLException {
    underlying.setLoginTimeout(seconds);
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return underlying.getLoginTimeout();
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return underlying.getParentLogger();
  }

  @Override
  public <T> T unwrap(final Class<T> iface) throws SQLException {
    return underlying.unwrap(iface);
  }

  @Override
  public boolean isWrapperFor(final Class<?> iface) throws SQLException {
    return underlying.isWrapperFor(iface);
  }

  @Override
  public void close() throws Exception {
    if (underlying instanceof AutoCloseable) {
      ((AutoCloseable) underlying).close();
    }
  }
}
