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

import static io.opentelemetry.instrumentation.jdbc.internal.JdbcSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.Ref;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;

class OpenTelemetryPreparedStatement<S extends PreparedStatement> extends OpenTelemetryStatement<S>
    implements PreparedStatement {

  public OpenTelemetryPreparedStatement(S preparedStatement, String query) {
    super(preparedStatement, query);
  }

  private static <T, E extends Exception> T wrapCall(
      PreparedStatement preparedStatement, CheckedCallable<T, E> callable) throws E {
    // Connection#getMetaData() may execute a Statement or PreparedStatement to retrieve DB info
    // this happens before the DB CLIENT span is started (and put in the current context), so this
    // instrumentation runs again and the shouldStartSpan() check always returns true - and so on
    // until we get a StackOverflowError
    // using CallDepth prevents this, because this check happens before Connection#getMetadata()
    // is called - the first recursive Statement call is just skipped and we do not create a span
    // for it
    if (CallDepthThreadLocalMap.getCallDepth(Statement.class).getAndIncrement() > 0) {
      return callable.call();
    }

    try {
      Context parentContext = Context.current();
      DbRequest request = DbRequest.create(preparedStatement);

      if (request == null || !instrumenter().shouldStart(parentContext, request)) {
        return callable.call();
      }

      Context context = instrumenter().start(parentContext, request);
      T result;
      try (Scope ignored = context.makeCurrent()) {
        result = callable.call();
      } catch (Throwable t) {
        instrumenter().end(context, request, null, t);
        throw t;
      }
      instrumenter().end(context, request, null, null);
      return result;
    } finally {
      CallDepthThreadLocalMap.reset(Statement.class);
    }
  }

  @Override
  public ResultSet executeQuery() throws SQLException {
    return wrapCall(delegate, delegate::executeQuery);
  }

  @Override
  public int executeUpdate() throws SQLException {
    return wrapCall(delegate, delegate::executeUpdate);
  }

  @Override
  public boolean execute() throws SQLException {
    return wrapCall(delegate, delegate::execute);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setNull(int parameterIndex, int sqlType) throws SQLException {
    delegate.setNull(parameterIndex, sqlType);
  }

  @Override
  public void setBoolean(int parameterIndex, boolean x) throws SQLException {
    delegate.setBoolean(parameterIndex, x);
  }

  @Override
  public void setByte(int parameterIndex, byte x) throws SQLException {
    delegate.setByte(parameterIndex, x);
  }

  @Override
  public void setShort(int parameterIndex, short x) throws SQLException {
    delegate.setShort(parameterIndex, x);
  }

  @Override
  public void setInt(int parameterIndex, int x) throws SQLException {
    delegate.setInt(parameterIndex, x);
  }

  @Override
  public void setLong(int parameterIndex, long x) throws SQLException {
    delegate.setLong(parameterIndex, x);
  }

  @Override
  public void setFloat(int parameterIndex, float x) throws SQLException {
    delegate.setFloat(parameterIndex, x);
  }

  @Override
  public void setDouble(int parameterIndex, double x) throws SQLException {
    delegate.setDouble(parameterIndex, x);
  }

  @Override
  public void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {
    delegate.setBigDecimal(parameterIndex, x);
  }

  @Override
  public void setString(int parameterIndex, String x) throws SQLException {
    delegate.setString(parameterIndex, x);
  }

  @Override
  public void setBytes(int parameterIndex, byte[] x) throws SQLException {
    delegate.setBytes(parameterIndex, x);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setDate(int parameterIndex, Date x) throws SQLException {
    delegate.setDate(parameterIndex, x);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setTime(int parameterIndex, Time x) throws SQLException {
    delegate.setTime(parameterIndex, x);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {
    delegate.setTimestamp(parameterIndex, x);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {
    delegate.setAsciiStream(parameterIndex, x, length);
  }

  @Override
  @Deprecated
  public void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {
    delegate.setUnicodeStream(parameterIndex, x, length);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {
    delegate.setBinaryStream(parameterIndex, x, length);
  }

  @Override
  public void clearParameters() throws SQLException {
    delegate.clearParameters();
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {
    delegate.setObject(parameterIndex, x, targetSqlType);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setObject(int parameterIndex, Object x) throws SQLException {
    delegate.setObject(parameterIndex, x);
  }

  @Override
  public void addBatch() throws SQLException {
    delegate.addBatch();
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setCharacterStream(int parameterIndex, Reader reader, int length)
      throws SQLException {
    delegate.setCharacterStream(parameterIndex, reader, length);
  }

  @Override
  public void setRef(int parameterIndex, Ref x) throws SQLException {
    delegate.setRef(parameterIndex, x);
  }

  @Override
  public void setBlob(int parameterIndex, Blob x) throws SQLException {
    delegate.setBlob(parameterIndex, x);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setClob(int parameterIndex, Clob x) throws SQLException {
    delegate.setClob(parameterIndex, x);
  }

  @Override
  public void setArray(int parameterIndex, Array x) throws SQLException {
    delegate.setArray(parameterIndex, x);
  }

  @Override
  public ResultSetMetaData getMetaData() throws SQLException {
    return delegate.getMetaData();
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {
    delegate.setDate(parameterIndex, x, cal);
  }

  @Override
  public void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {
    delegate.setTime(parameterIndex, x, cal);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {
    delegate.setTimestamp(parameterIndex, x, cal);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {
    delegate.setNull(parameterIndex, sqlType, typeName);
  }

  @Override
  public void setURL(int parameterIndex, URL x) throws SQLException {
    delegate.setURL(parameterIndex, x);
  }

  @Override
  public ParameterMetaData getParameterMetaData() throws SQLException {
    return delegate.getParameterMetaData();
  }

  @Override
  public void setRowId(int parameterIndex, RowId x) throws SQLException {
    delegate.setRowId(parameterIndex, x);
  }

  @Override
  public void setNString(int parameterIndex, String value) throws SQLException {
    delegate.setNString(parameterIndex, value);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setNCharacterStream(int parameterIndex, Reader value, long length)
      throws SQLException {
    delegate.setNCharacterStream(parameterIndex, value, length);
  }

  @Override
  public void setNClob(int parameterIndex, NClob value) throws SQLException {
    delegate.setNClob(parameterIndex, value);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setClob(int parameterIndex, Reader reader, long length) throws SQLException {
    delegate.setClob(parameterIndex, reader, length);
  }

  @Override
  public void setBlob(int parameterIndex, InputStream inputStream, long length)
      throws SQLException {
    delegate.setBlob(parameterIndex, inputStream, length);
  }

  @Override
  public void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {
    delegate.setNClob(parameterIndex, reader, length);
  }

  @Override
  public void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {
    delegate.setSQLXML(parameterIndex, xmlObject);
  }

  @Override
  public void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength)
      throws SQLException {
    delegate.setObject(parameterIndex, x, targetSqlType, scaleOrLength);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {
    delegate.setAsciiStream(parameterIndex, x, length);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {
    delegate.setBinaryStream(parameterIndex, x, length);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setCharacterStream(int parameterIndex, Reader reader, long length)
      throws SQLException {
    delegate.setCharacterStream(parameterIndex, reader, length);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {
    delegate.setAsciiStream(parameterIndex, x);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {
    delegate.setBinaryStream(parameterIndex, x);
  }

  @Override
  public void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {
    delegate.setCharacterStream(parameterIndex, reader);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {
    delegate.setNCharacterStream(parameterIndex, value);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setClob(int parameterIndex, Reader reader) throws SQLException {
    delegate.setClob(parameterIndex, reader);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {
    delegate.setBlob(parameterIndex, inputStream);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setNClob(int parameterIndex, Reader reader) throws SQLException {
    delegate.setNClob(parameterIndex, reader);
  }
}
