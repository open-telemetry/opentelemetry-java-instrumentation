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

package io.opentelemetry.instrumentation.jdbc.internal;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.NClob;
import java.sql.PreparedStatement;
import java.sql.SQLClientInfoException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.ShardingKey;
import java.sql.Statement;
import java.sql.Struct;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class OpenTelemetryConnection implements Connection {

  private static final boolean hasJdbc43 = hasJdbc43();
  protected final Connection delegate;
  private final DbInfo dbInfo;
  protected final Instrumenter<DbRequest, Void> statementInstrumenter;
  protected final Instrumenter<DbRequest, Void> transactionInstrumenter;
  private final boolean captureQueryParameters;

  protected OpenTelemetryConnection(
      Connection delegate,
      DbInfo dbInfo,
      Instrumenter<DbRequest, Void> statementInstrumenter,
      Instrumenter<DbRequest, Void> transactionInstrumenter,
      boolean captureQueryParameters) {
    this.delegate = delegate;
    this.dbInfo = dbInfo;
    this.statementInstrumenter = statementInstrumenter;
    this.transactionInstrumenter = transactionInstrumenter;
    this.captureQueryParameters = captureQueryParameters;
  }

  // visible for testing
  static boolean hasJdbc43() {
    try {
      Class.forName("java.sql.ShardingKey");
      return true;
    } catch (ClassNotFoundException exception) {
      return false;
    }
  }

  public static Connection create(
      Connection delegate,
      DbInfo dbInfo,
      Instrumenter<DbRequest, Void> statementInstrumenter,
      Instrumenter<DbRequest, Void> transactionInstrumenter,
      boolean captureQueryParameters) {
    if (hasJdbc43) {
      return new OpenTelemetryConnectionJdbc43(
          delegate, dbInfo, statementInstrumenter, transactionInstrumenter, captureQueryParameters);
    }
    return new OpenTelemetryConnection(
        delegate, dbInfo, statementInstrumenter, transactionInstrumenter, captureQueryParameters);
  }

  @Override
  public Statement createStatement() throws SQLException {
    Statement statement = delegate.createStatement();
    return new OpenTelemetryStatement<>(statement, this, dbInfo, statementInstrumenter);
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency)
      throws SQLException {
    Statement statement = delegate.createStatement(resultSetType, resultSetConcurrency);
    return new OpenTelemetryStatement<>(statement, this, dbInfo, statementInstrumenter);
  }

  @Override
  public Statement createStatement(
      int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
    Statement statement =
        delegate.createStatement(resultSetType, resultSetConcurrency, resultSetHoldability);
    return new OpenTelemetryStatement<>(statement, this, dbInfo, statementInstrumenter);
  }

  @Override
  public PreparedStatement prepareStatement(String sql) throws SQLException {
    PreparedStatement statement = delegate.prepareStatement(sql);
    return new OpenTelemetryPreparedStatement<>(
        statement, this, dbInfo, sql, statementInstrumenter, captureQueryParameters);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
      throws SQLException {
    PreparedStatement statement =
        delegate.prepareStatement(sql, resultSetType, resultSetConcurrency);
    return new OpenTelemetryPreparedStatement<>(
        statement, this, dbInfo, sql, statementInstrumenter, captureQueryParameters);
  }

  @Override
  public PreparedStatement prepareStatement(
      String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
      throws SQLException {
    PreparedStatement statement =
        delegate.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    return new OpenTelemetryPreparedStatement<>(
        statement, this, dbInfo, sql, statementInstrumenter, captureQueryParameters);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
    PreparedStatement statement = delegate.prepareStatement(sql, autoGeneratedKeys);
    return new OpenTelemetryPreparedStatement<>(
        statement, this, dbInfo, sql, statementInstrumenter, captureQueryParameters);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
    PreparedStatement statement = delegate.prepareStatement(sql, columnIndexes);
    return new OpenTelemetryPreparedStatement<>(
        statement, this, dbInfo, sql, statementInstrumenter, captureQueryParameters);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
    PreparedStatement statement = delegate.prepareStatement(sql, columnNames);
    return new OpenTelemetryPreparedStatement<>(
        statement, this, dbInfo, sql, statementInstrumenter, captureQueryParameters);
  }

  @Override
  public CallableStatement prepareCall(String sql) throws SQLException {
    CallableStatement statement = delegate.prepareCall(sql);
    return new OpenTelemetryCallableStatement<>(
        statement, this, dbInfo, sql, statementInstrumenter, captureQueryParameters);
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
      throws SQLException {
    CallableStatement statement = delegate.prepareCall(sql, resultSetType, resultSetConcurrency);
    return new OpenTelemetryCallableStatement<>(
        statement, this, dbInfo, sql, statementInstrumenter, captureQueryParameters);
  }

  @Override
  public CallableStatement prepareCall(
      String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
      throws SQLException {
    CallableStatement statement =
        delegate.prepareCall(sql, resultSetType, resultSetConcurrency, resultSetHoldability);
    return new OpenTelemetryCallableStatement<>(
        statement, this, dbInfo, sql, statementInstrumenter, captureQueryParameters);
  }

  @Override
  public void commit() throws SQLException {
    wrapCall(delegate::commit, "COMMIT");
  }

  @Override
  public void close() throws SQLException {
    delegate.close();
  }

  @Override
  public String nativeSQL(String sql) throws SQLException {
    return delegate.nativeSQL(sql);
  }

  @Override
  public boolean getAutoCommit() throws SQLException {
    return delegate.getAutoCommit();
  }

  @Override
  public void setAutoCommit(boolean autoCommit) throws SQLException {
    delegate.setAutoCommit(autoCommit);
  }

  @Override
  public boolean isClosed() throws SQLException {
    return delegate.isClosed();
  }

  @Override
  public DatabaseMetaData getMetaData() throws SQLException {
    return delegate.getMetaData();
  }

  @Override
  public boolean isReadOnly() throws SQLException {
    return delegate.isReadOnly();
  }

  @Override
  public void setReadOnly(boolean readOnly) throws SQLException {
    delegate.setReadOnly(readOnly);
  }

  @Override
  public String getCatalog() throws SQLException {
    return delegate.getCatalog();
  }

  @Override
  public void setCatalog(String catalog) throws SQLException {
    delegate.setCatalog(catalog);
  }

  @Override
  public int getTransactionIsolation() throws SQLException {
    return delegate.getTransactionIsolation();
  }

  @Override
  public void setTransactionIsolation(int level) throws SQLException {
    delegate.setTransactionIsolation(level);
  }

  @Override
  public SQLWarning getWarnings() throws SQLException {
    return delegate.getWarnings();
  }

  @Override
  public void clearWarnings() throws SQLException {
    delegate.clearWarnings();
  }

  @Override
  public Map<String, Class<?>> getTypeMap() throws SQLException {
    return delegate.getTypeMap();
  }

  @Override
  public void setTypeMap(Map<String, Class<?>> map) throws SQLException {
    delegate.setTypeMap(map);
  }

  @Override
  public int getHoldability() throws SQLException {
    return delegate.getHoldability();
  }

  @Override
  public void setHoldability(int holdability) throws SQLException {
    delegate.setHoldability(holdability);
  }

  @Override
  public Savepoint setSavepoint() throws SQLException {
    return delegate.setSavepoint();
  }

  @Override
  public Savepoint setSavepoint(String name) throws SQLException {
    return delegate.setSavepoint(name);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void rollback() throws SQLException {
    wrapCall(delegate::rollback, "ROLLBACK");
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void rollback(Savepoint savepoint) throws SQLException {
    wrapCall(() -> delegate.rollback(savepoint), "ROLLBACK");
  }

  @Override
  public void releaseSavepoint(Savepoint savepoint) throws SQLException {
    delegate.releaseSavepoint(savepoint);
  }

  @Override
  public Clob createClob() throws SQLException {
    return delegate.createClob();
  }

  @Override
  public Blob createBlob() throws SQLException {
    return delegate.createBlob();
  }

  @Override
  public NClob createNClob() throws SQLException {
    return delegate.createNClob();
  }

  @Override
  public SQLXML createSQLXML() throws SQLException {
    return delegate.createSQLXML();
  }

  @Override
  public boolean isValid(int timeout) throws SQLException {
    return delegate.isValid(timeout);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setClientInfo(String name, String value) throws SQLClientInfoException {
    delegate.setClientInfo(name, value);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setClientInfo(Properties properties) throws SQLClientInfoException {
    delegate.setClientInfo(properties);
  }

  @Override
  public String getClientInfo(String name) throws SQLException {
    return delegate.getClientInfo(name);
  }

  @Override
  public Properties getClientInfo() throws SQLException {
    return delegate.getClientInfo();
  }

  @Override
  public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
    return delegate.createArrayOf(typeName, elements);
  }

  @Override
  public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
    return delegate.createStruct(typeName, attributes);
  }

  @Override
  public String getSchema() throws SQLException {
    return delegate.getSchema();
  }

  @Override
  public void setSchema(String schema) throws SQLException {
    delegate.setSchema(schema);
  }

  @Override
  public void abort(Executor executor) throws SQLException {
    delegate.abort(executor);
  }

  @Override
  public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {
    delegate.setNetworkTimeout(executor, milliseconds);
  }

  @Override
  public int getNetworkTimeout() throws SQLException {
    return delegate.getNetworkTimeout();
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    return delegate.unwrap(iface);
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return delegate.isWrapperFor(iface);
  }

  // visible for testing
  public DbInfo getDbInfo() {
    return dbInfo;
  }

  // JDBC 4.3
  static class OpenTelemetryConnectionJdbc43 extends OpenTelemetryConnection {
    OpenTelemetryConnectionJdbc43(
        Connection delegate,
        DbInfo dbInfo,
        Instrumenter<DbRequest, Void> statementInstrumenter,
        Instrumenter<DbRequest, Void> transactionInstrumenter,
        boolean captureQueryParameters) {
      super(
          delegate, dbInfo, statementInstrumenter, transactionInstrumenter, captureQueryParameters);
    }

    @SuppressWarnings("Since15")
    @Override
    public void beginRequest() throws SQLException {
      delegate.beginRequest();
    }

    @SuppressWarnings("Since15")
    @Override
    public void endRequest() throws SQLException {
      delegate.endRequest();
    }

    @SuppressWarnings("Since15")
    @Override
    public boolean setShardingKeyIfValid(
        ShardingKey shardingKey, ShardingKey superShardingKey, int timeout) throws SQLException {
      return delegate.setShardingKeyIfValid(shardingKey, superShardingKey, timeout);
    }

    @SuppressWarnings("Since15")
    @Override
    public boolean setShardingKeyIfValid(ShardingKey shardingKey, int timeout) throws SQLException {
      return delegate.setShardingKeyIfValid(shardingKey, timeout);
    }

    @SuppressWarnings("Since15")
    @Override
    public void setShardingKey(ShardingKey shardingKey, ShardingKey superShardingKey)
        throws SQLException {
      delegate.setShardingKey(shardingKey, superShardingKey);
    }

    @SuppressWarnings("Since15")
    @Override
    public void setShardingKey(ShardingKey shardingKey) throws SQLException {
      delegate.setShardingKey(shardingKey);
    }
  }

  protected <E extends Exception> void wrapCall(ThrowingSupplier<E> callable, String operation)
      throws E {
    Context parentContext = Context.current();
    DbRequest request = DbRequest.createTransaction(dbInfo, operation);
    if (!this.transactionInstrumenter.shouldStart(parentContext, request)) {
      callable.call();
      return;
    }

    Context context = this.transactionInstrumenter.start(parentContext, request);
    try (Scope ignored = context.makeCurrent()) {
      callable.call();
    } catch (Throwable t) {
      this.transactionInstrumenter.end(context, request, null, t);
      throw t;
    }
    this.transactionInstrumenter.end(context, request, null, null);
  }

  protected interface ThrowingSupplier<E extends Exception> {
    void call() throws E;
  }
}
