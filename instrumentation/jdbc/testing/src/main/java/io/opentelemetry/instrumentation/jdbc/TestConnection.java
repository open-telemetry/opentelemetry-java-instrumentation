/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc;

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
import java.sql.Statement;
import java.sql.Struct;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;

/** A JDBC connection class that optionally throws an exception in the constructor, used to test */
public class TestConnection implements Connection {
  private String url;

  public TestConnection() {
    this(false);
  }

  public TestConnection(boolean throwException) {
    if (throwException) {
      throw new IllegalStateException("connection exception");
    }
  }

  @Override
  public void abort(Executor executor) throws SQLException {}

  @Override
  public void clearWarnings() throws SQLException {}

  @Override
  public void close() throws SQLException {}

  @Override
  public void commit() throws SQLException {}

  @Override
  public Array createArrayOf(String typeName, Object[] elements) throws SQLException {
    return null;
  }

  @Override
  public Blob createBlob() throws SQLException {
    return null;
  }

  @Override
  public Clob createClob() throws SQLException {
    return null;
  }

  @Override
  public NClob createNClob() throws SQLException {
    return null;
  }

  @Override
  public SQLXML createSQLXML() throws SQLException {
    return null;
  }

  @Override
  public Statement createStatement() throws SQLException {
    return new TestStatement(this);
  }

  @Override
  public Statement createStatement(int resultSetType, int resultSetConcurrency)
      throws SQLException {
    return new TestStatement(this);
  }

  @Override
  public Statement createStatement(
      int resultSetType, int resultSetConcurrency, int resultSetHoldability) throws SQLException {
    return new TestStatement(this);
  }

  @Override
  public Struct createStruct(String typeName, Object[] attributes) throws SQLException {
    return null;
  }

  @Override
  public boolean getAutoCommit() throws SQLException {
    return false;
  }

  @Override
  public String getCatalog() throws SQLException {
    return null;
  }

  @Override
  public String getClientInfo(String name) throws SQLException {
    throw new UnsupportedOperationException("Test 123");
  }

  @Override
  public Properties getClientInfo() throws SQLException {
    throw new UnsupportedOperationException("Test 123");
  }

  @Override
  public int getHoldability() throws SQLException {
    return 0;
  }

  @Override
  public DatabaseMetaData getMetaData() throws SQLException {
    if (url != null) {
      return new TestDatabaseMetaData(url);
    }
    return new TestDatabaseMetaData();
  }

  @Override
  public int getNetworkTimeout() throws SQLException {
    return 0;
  }

  @Override
  public String getSchema() throws SQLException {
    return null;
  }

  @Override
  public int getTransactionIsolation() throws SQLException {
    return 0;
  }

  @Override
  public Map<String, Class<?>> getTypeMap() throws SQLException {
    return new HashMap<>();
  }

  @Override
  public SQLWarning getWarnings() throws SQLException {
    return null;
  }

  @Override
  public boolean isClosed() throws SQLException {
    return false;
  }

  @Override
  public boolean isReadOnly() throws SQLException {
    return false;
  }

  @Override
  public boolean isValid(int timeout) throws SQLException {
    return false;
  }

  @Override
  public boolean isWrapperFor(Class<?> iface) throws SQLException {
    return false;
  }

  @Override
  public String nativeSQL(String sql) throws SQLException {
    return null;
  }

  @Override
  public CallableStatement prepareCall(String sql) throws SQLException {
    return new TestCallableStatement();
  }

  @Override
  public CallableStatement prepareCall(String sql, int resultSetType, int resultSetConcurrency)
      throws SQLException {
    return new TestCallableStatement();
  }

  @Override
  public CallableStatement prepareCall(
      String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
      throws SQLException {
    return new TestCallableStatement();
  }

  @Override
  public PreparedStatement prepareStatement(String sql) throws SQLException {
    return new TestPreparedStatement(this);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int resultSetType, int resultSetConcurrency)
      throws SQLException {
    return new TestPreparedStatement(this);
  }

  @Override
  public PreparedStatement prepareStatement(
      String sql, int resultSetType, int resultSetConcurrency, int resultSetHoldability)
      throws SQLException {
    return new TestPreparedStatement(this);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int autoGeneratedKeys) throws SQLException {
    return new TestPreparedStatement(this);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, int[] columnIndexes) throws SQLException {
    return new TestPreparedStatement(this);
  }

  @Override
  public PreparedStatement prepareStatement(String sql, String[] columnNames) throws SQLException {
    return new TestPreparedStatement(this);
  }

  @Override
  public void releaseSavepoint(Savepoint savepoint) throws SQLException {}

  @Override
  public void rollback(Savepoint savepoint) throws SQLException {}

  @Override
  public void rollback() throws SQLException {}

  @Override
  public void setAutoCommit(boolean autoCommit) throws SQLException {}

  @Override
  public void setCatalog(String catalog) throws SQLException {}

  @Override
  public void setClientInfo(Properties properties) throws SQLClientInfoException {}

  @Override
  public void setClientInfo(String name, String value) throws SQLClientInfoException {}

  @Override
  public void setHoldability(int holdability) throws SQLException {}

  @Override
  public void setNetworkTimeout(Executor executor, int milliseconds) throws SQLException {}

  @Override
  public void setReadOnly(boolean readOnly) throws SQLException {}

  @Override
  public Savepoint setSavepoint() throws SQLException {
    return null;
  }

  @Override
  public Savepoint setSavepoint(String name) throws SQLException {
    return null;
  }

  @Override
  public void setSchema(String schema) throws SQLException {}

  @Override
  public void setTransactionIsolation(int level) throws SQLException {}

  @Override
  public void setTypeMap(Map<String, Class<?>> map) throws SQLException {}

  public void setUrl(String url) {
    this.url = url;
  }

  @Override
  public <T> T unwrap(Class<T> iface) throws SQLException {
    return null;
  }
}
