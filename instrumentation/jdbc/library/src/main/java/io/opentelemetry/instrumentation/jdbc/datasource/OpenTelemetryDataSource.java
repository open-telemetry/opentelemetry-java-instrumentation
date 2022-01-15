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

package io.opentelemetry.instrumentation.jdbc.datasource;

import static io.opentelemetry.instrumentation.jdbc.internal.DataSourceSingletons.instrumenter;
import static io.opentelemetry.instrumentation.jdbc.internal.JdbcUtils.computeDbInfo;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.jdbc.internal.DbInfo;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection;
import io.opentelemetry.instrumentation.jdbc.internal.ThrowingSupplier;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/** OpenTelemetry {@link DataSource} implementation. */
public class OpenTelemetryDataSource implements DataSource, AutoCloseable {

  private final DataSource delegate;

  /**
   * Create a OpenTelemetry DataSource wrapping another DataSource. This constructor is primarily
   * used by dependency injection frameworks.
   *
   * @param delegate the DataSource to wrap
   */
  public OpenTelemetryDataSource(DataSource delegate) {
    this.delegate = delegate;
  }

  @Override
  public Connection getConnection() throws SQLException {
    Connection connection = wrapCall(delegate::getConnection);
    DbInfo dbInfo = computeDbInfo(connection);
    return new OpenTelemetryConnection(connection, dbInfo);
  }

  @Override
  public Connection getConnection(String username, String password) throws SQLException {
    Connection connection = wrapCall(() -> delegate.getConnection(username, password));
    DbInfo dbInfo = computeDbInfo(connection);
    return new OpenTelemetryConnection(connection, dbInfo);
  }

  @Override
  public PrintWriter getLogWriter() throws SQLException {
    return delegate.getLogWriter();
  }

  @Override
  public void setLogWriter(PrintWriter out) throws SQLException {
    delegate.setLogWriter(out);
  }

  @Override
  public int getLoginTimeout() throws SQLException {
    return delegate.getLoginTimeout();
  }

  @Override
  public void setLoginTimeout(int seconds) throws SQLException {
    delegate.setLoginTimeout(seconds);
  }

  @Override
  public Logger getParentLogger() throws SQLFeatureNotSupportedException {
    return delegate.getParentLogger();
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    return delegate.unwrap(iface);
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return delegate.isWrapperFor(iface);
  }

  @Override
  public void close() throws Exception {
    if (delegate instanceof AutoCloseable) {
      ((AutoCloseable) delegate).close();
    }
  }

  private <T, E extends SQLException> T wrapCall(ThrowingSupplier<T, E> callable) throws E {
    Context parentContext = Context.current();

    if (!Span.fromContext(parentContext).getSpanContext().isValid()) {
      // this instrumentation is already very noisy, and calls to getConnection outside of an
      // existing trace do not tend to be very interesting
      return callable.call();
    }

    Context context = instrumenter().start(parentContext, delegate);
    T result;
    try (Scope ignored = context.makeCurrent()) {
      result = callable.call();
    } catch (Throwable t) {
      instrumenter().end(context, delegate, null, t);
      throw t;
    }
    instrumenter().end(context, delegate, null, null);
    return result;
  }
}
