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

import java.io.InputStream;
import java.io.Reader;
import java.math.BigDecimal;
import java.net.URL;
import java.sql.Array;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Map;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class OpenTelemetryCallableStatement<S extends CallableStatement>
    extends OpenTelemetryPreparedStatement<S> implements CallableStatement {

  public OpenTelemetryCallableStatement(S delegate, DbInfo dbInfo, String query) {
    super(delegate, dbInfo, query);
  }

  @Override
  public boolean wasNull() throws SQLException {
    return delegate.wasNull();
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public String getString(int parameterIndex) throws SQLException {
    return delegate.getString(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public String getString(String parameterName) throws SQLException {
    return delegate.getString(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public boolean getBoolean(int parameterIndex) throws SQLException {
    return delegate.getBoolean(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public boolean getBoolean(String parameterName) throws SQLException {
    return delegate.getBoolean(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public byte getByte(int parameterIndex) throws SQLException {
    return delegate.getByte(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public byte getByte(String parameterName) throws SQLException {
    return delegate.getByte(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public short getShort(int parameterIndex) throws SQLException {
    return delegate.getShort(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public short getShort(String parameterName) throws SQLException {
    return delegate.getShort(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public int getInt(int parameterIndex) throws SQLException {
    return delegate.getInt(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public int getInt(String parameterName) throws SQLException {
    return delegate.getInt(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public long getLong(int parameterIndex) throws SQLException {
    return delegate.getLong(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public long getLong(String parameterName) throws SQLException {
    return delegate.getLong(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public float getFloat(int parameterIndex) throws SQLException {
    return delegate.getFloat(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public float getFloat(String parameterName) throws SQLException {
    return delegate.getFloat(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public double getDouble(int parameterIndex) throws SQLException {
    return delegate.getDouble(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public double getDouble(String parameterName) throws SQLException {
    return delegate.getDouble(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public byte[] getBytes(int parameterIndex) throws SQLException {
    return delegate.getBytes(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public byte[] getBytes(String parameterName) throws SQLException {
    return delegate.getBytes(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Time getTime(int parameterIndex) throws SQLException {
    return delegate.getTime(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Time getTime(String parameterName, Calendar cal) throws SQLException {
    return delegate.getTime(parameterName, cal);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Time getTime(int parameterIndex, Calendar cal) throws SQLException {
    return delegate.getTime(parameterIndex, cal);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Time getTime(String parameterName) throws SQLException {
    return delegate.getTime(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Timestamp getTimestamp(int parameterIndex) throws SQLException {
    return delegate.getTimestamp(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Timestamp getTimestamp(String parameterName, Calendar cal) throws SQLException {
    return delegate.getTimestamp(parameterName, cal);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Timestamp getTimestamp(String parameterName) throws SQLException {
    return delegate.getTimestamp(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException {
    return delegate.getTimestamp(parameterIndex, cal);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Object getObject(int parameterIndex) throws SQLException {
    return delegate.getObject(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Object getObject(int parameterIndex, Map<String, Class<?>> map) throws SQLException {
    return delegate.getObject(parameterIndex, map);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Object getObject(String parameterName) throws SQLException {
    return delegate.getObject(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public <T> T getObject(int parameterIndex, Class<T> type) throws SQLException {
    return delegate.getObject(parameterIndex, type);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public <T> T getObject(String parameterName, Class<T> type) throws SQLException {
    return delegate.getObject(parameterName, type);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Object getObject(String parameterName, Map<String, Class<?>> map) throws SQLException {
    return delegate.getObject(parameterName, map);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Ref getRef(int parameterIndex) throws SQLException {
    return delegate.getRef(parameterIndex);
  }

  @Override
  public Ref getRef(String parameterName) throws SQLException {
    return delegate.getRef(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Blob getBlob(int parameterIndex) throws SQLException {
    return delegate.getBlob(parameterIndex);
  }

  @Override
  public Blob getBlob(String parameterName) throws SQLException {
    return delegate.getBlob(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Clob getClob(int parameterIndex) throws SQLException {
    return delegate.getClob(parameterIndex);
  }

  @Override
  public Clob getClob(String parameterName) throws SQLException {
    return delegate.getClob(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Array getArray(int parameterIndex) throws SQLException {
    return delegate.getArray(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Array getArray(String parameterName) throws SQLException {
    return delegate.getArray(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void registerOutParameter(int parameterIndex, int sqlType, String typeName)
      throws SQLException {
    delegate.registerOutParameter(parameterIndex, sqlType, typeName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void registerOutParameter(String parameterName, int sqlType) throws SQLException {
    delegate.registerOutParameter(parameterName, sqlType);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void registerOutParameter(String parameterName, int sqlType, int scale)
      throws SQLException {
    delegate.registerOutParameter(parameterName, sqlType, scale);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void registerOutParameter(String parameterName, int sqlType, String typeName)
      throws SQLException {
    delegate.registerOutParameter(parameterName, sqlType, typeName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void registerOutParameter(int parameterIndex, int sqlType) throws SQLException {
    delegate.registerOutParameter(parameterIndex, sqlType);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException {
    delegate.registerOutParameter(parameterIndex, sqlType, scale);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public URL getURL(int parameterIndex) throws SQLException {
    return delegate.getURL(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public URL getURL(String parameterName) throws SQLException {
    return delegate.getURL(parameterName);
  }

  @Override
  public void setURL(String parameterName, URL val) throws SQLException {
    delegate.setURL(parameterName, val);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setNull(String parameterName, int sqlType) throws SQLException {
    delegate.setNull(parameterName, sqlType);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setNull(String parameterName, int sqlType, String typeName) throws SQLException {
    delegate.setNull(parameterName, sqlType, typeName);
  }

  @Override
  public void setBoolean(String parameterName, boolean x) throws SQLException {
    delegate.setBoolean(parameterName, x);
  }

  @Override
  public void setByte(String parameterName, byte x) throws SQLException {
    delegate.setByte(parameterName, x);
  }

  @Override
  public void setShort(String parameterName, short x) throws SQLException {
    delegate.setShort(parameterName, x);
  }

  @Override
  public void setInt(String parameterName, int x) throws SQLException {
    delegate.setInt(parameterName, x);
  }

  @Override
  public void setLong(String parameterName, long x) throws SQLException {
    delegate.setLong(parameterName, x);
  }

  @Override
  public void setFloat(String parameterName, float x) throws SQLException {
    delegate.setFloat(parameterName, x);
  }

  @Override
  public void setDouble(String parameterName, double x) throws SQLException {
    delegate.setDouble(parameterName, x);
  }

  @Override
  public void setBigDecimal(String parameterName, BigDecimal x) throws SQLException {
    delegate.setBigDecimal(parameterName, x);
  }

  @Override
  public void setString(String parameterName, String x) throws SQLException {
    delegate.setString(parameterName, x);
  }

  @Override
  public void setBytes(String parameterName, byte[] x) throws SQLException {
    delegate.setBytes(parameterName, x);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setDate(String parameterName, Date x) throws SQLException {
    delegate.setDate(parameterName, x);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setDate(String parameterName, Date x, Calendar cal) throws SQLException {
    delegate.setDate(parameterName, x, cal);
  }

  @Override
  public void setTime(String parameterName, Time x) throws SQLException {
    delegate.setTime(parameterName, x);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setTime(String parameterName, Time x, Calendar cal) throws SQLException {
    delegate.setTime(parameterName, x, cal);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setTimestamp(String parameterName, Timestamp x) throws SQLException {
    delegate.setTimestamp(parameterName, x);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException {
    delegate.setTimestamp(parameterName, x, cal);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setAsciiStream(String parameterName, InputStream x, int length) throws SQLException {
    delegate.setAsciiStream(parameterName, x, length);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setAsciiStream(String parameterName, InputStream x, long length) throws SQLException {
    delegate.setAsciiStream(parameterName, x, length);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setAsciiStream(String parameterName, InputStream x) throws SQLException {
    delegate.setAsciiStream(parameterName, x);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setBinaryStream(String parameterName, InputStream x, int length) throws SQLException {
    delegate.setBinaryStream(parameterName, x, length);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setBinaryStream(String parameterName, InputStream x, long length)
      throws SQLException {
    delegate.setBinaryStream(parameterName, x, length);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setBinaryStream(String parameterName, InputStream x) throws SQLException {
    delegate.setBinaryStream(parameterName, x);
  }

  @Override
  public void setObject(String parameterName, Object x, int targetSqlType, int scale)
      throws SQLException {
    delegate.setObject(parameterName, x, targetSqlType, scale);
  }

  @Override
  public void setObject(String parameterName, Object x, int targetSqlType) throws SQLException {
    delegate.setObject(parameterName, x, targetSqlType);
  }

  @Override
  public void setObject(String parameterName, Object x) throws SQLException {
    delegate.setObject(parameterName, x);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setCharacterStream(String parameterName, Reader reader, int length)
      throws SQLException {
    delegate.setCharacterStream(parameterName, reader, length);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setCharacterStream(String parameterName, Reader reader, long length)
      throws SQLException {
    delegate.setCharacterStream(parameterName, reader, length);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setCharacterStream(String parameterName, Reader reader) throws SQLException {
    delegate.setCharacterStream(parameterName, reader);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Date getDate(String parameterName) throws SQLException {
    return delegate.getDate(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Date getDate(int parameterIndex, Calendar cal) throws SQLException {
    return delegate.getDate(parameterIndex, cal);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Date getDate(int parameterIndex) throws SQLException {
    return delegate.getDate(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public Date getDate(String parameterName, Calendar cal) throws SQLException {
    return delegate.getDate(parameterName, cal);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public BigDecimal getBigDecimal(String parameterName) throws SQLException {
    return delegate.getBigDecimal(parameterName);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
    return delegate.getBigDecimal(parameterIndex);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  @Deprecated
  public BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException {
    return delegate.getBigDecimal(parameterIndex, scale);
  }

  @Override
  public RowId getRowId(int parameterIndex) throws SQLException {
    return delegate.getRowId(parameterIndex);
  }

  @Override
  public RowId getRowId(String parameterName) throws SQLException {
    return delegate.getRowId(parameterName);
  }

  @Override
  public void setRowId(String parameterName, RowId x) throws SQLException {
    delegate.setRowId(parameterName, x);
  }

  @Override
  public void setNString(String parameterName, String value) throws SQLException {
    delegate.setNString(parameterName, value);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setNCharacterStream(String parameterName, Reader value, long length)
      throws SQLException {
    delegate.setNCharacterStream(parameterName, value, length);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setNCharacterStream(String parameterName, Reader value) throws SQLException {
    delegate.setNCharacterStream(parameterName, value);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setNClob(String parameterName, NClob value) throws SQLException {
    delegate.setNClob(parameterName, value);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setNClob(String parameterName, Reader reader) throws SQLException {
    delegate.setNClob(parameterName, reader);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setNClob(String parameterName, Reader reader, long length) throws SQLException {
    delegate.setNClob(parameterName, reader, length);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setClob(String parameterName, Reader reader, long length) throws SQLException {
    delegate.setClob(parameterName, reader, length);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setClob(String parameterName, Reader reader) throws SQLException {
    delegate.setClob(parameterName, reader);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setClob(String parameterName, Clob x) throws SQLException {
    delegate.setClob(parameterName, x);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setBlob(String parameterName, InputStream inputStream, long length)
      throws SQLException {
    delegate.setBlob(parameterName, inputStream, length);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setBlob(String parameterName, InputStream inputStream) throws SQLException {
    delegate.setBlob(parameterName, inputStream);
  }

  @SuppressWarnings("UngroupedOverloads")
  @Override
  public void setBlob(String parameterName, Blob x) throws SQLException {
    delegate.setBlob(parameterName, x);
  }

  @Override
  public NClob getNClob(int parameterIndex) throws SQLException {
    return delegate.getNClob(parameterIndex);
  }

  @Override
  public NClob getNClob(String parameterName) throws SQLException {
    return delegate.getNClob(parameterName);
  }

  @Override
  public void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException {
    delegate.setSQLXML(parameterName, xmlObject);
  }

  @Override
  public SQLXML getSQLXML(int parameterIndex) throws SQLException {
    return delegate.getSQLXML(parameterIndex);
  }

  @Override
  public SQLXML getSQLXML(String parameterName) throws SQLException {
    return delegate.getSQLXML(parameterName);
  }

  @Override
  public String getNString(int parameterIndex) throws SQLException {
    return delegate.getNString(parameterIndex);
  }

  @Override
  public String getNString(String parameterName) throws SQLException {
    return delegate.getNString(parameterName);
  }

  @Override
  public Reader getNCharacterStream(int parameterIndex) throws SQLException {
    return delegate.getNCharacterStream(parameterIndex);
  }

  @Override
  public Reader getNCharacterStream(String parameterName) throws SQLException {
    return delegate.getNCharacterStream(parameterName);
  }

  @Override
  public Reader getCharacterStream(int parameterIndex) throws SQLException {
    return delegate.getCharacterStream(parameterIndex);
  }

  @Override
  public Reader getCharacterStream(String parameterName) throws SQLException {
    return delegate.getCharacterStream(parameterName);
  }
}
