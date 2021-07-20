/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc


import java.sql.Array
import java.sql.Blob
import java.sql.CallableStatement
import java.sql.Clob
import java.sql.Date
import java.sql.NClob
import java.sql.Ref
import java.sql.RowId
import java.sql.SQLException
import java.sql.SQLXML
import java.sql.Time
import java.sql.Timestamp

class TestCallableStatement extends TestPreparedStatement implements CallableStatement {
  @Override
  void registerOutParameter(int parameterIndex, int sqlType) throws SQLException {

  }

  @Override
  void registerOutParameter(int parameterIndex, int sqlType, int scale) throws SQLException {

  }

  @Override
  boolean wasNull() throws SQLException {
    return false
  }

  @Override
  String getString(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  boolean getBoolean(int parameterIndex) throws SQLException {
    return false
  }

  @Override
  byte getByte(int parameterIndex) throws SQLException {
    return 0
  }

  @Override
  short getShort(int parameterIndex) throws SQLException {
    return 0
  }

  @Override
  int getInt(int parameterIndex) throws SQLException {
    return 0
  }

  @Override
  long getLong(int parameterIndex) throws SQLException {
    return 0
  }

  @Override
  float getFloat(int parameterIndex) throws SQLException {
    return 0
  }

  @Override
  double getDouble(int parameterIndex) throws SQLException {
    return 0
  }

  @Override
  BigDecimal getBigDecimal(int parameterIndex, int scale) throws SQLException {
    return null
  }

  @Override
  byte[] getBytes(int parameterIndex) throws SQLException {
    return new byte[0]
  }

  @Override
  Date getDate(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  Time getTime(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  Timestamp getTimestamp(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  Object getObject(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  BigDecimal getBigDecimal(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  Object getObject(int parameterIndex, Map<String, Class<?>> map) throws SQLException {
    return null
  }

  @Override
  Ref getRef(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  Blob getBlob(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  Clob getClob(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  Array getArray(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  Date getDate(int parameterIndex, Calendar cal) throws SQLException {
    return null
  }

  @Override
  Time getTime(int parameterIndex, Calendar cal) throws SQLException {
    return null
  }

  @Override
  Timestamp getTimestamp(int parameterIndex, Calendar cal) throws SQLException {
    return null
  }

  @Override
  void registerOutParameter(int parameterIndex, int sqlType, String typeName) throws SQLException {

  }

  @Override
  void registerOutParameter(String parameterName, int sqlType) throws SQLException {

  }

  @Override
  void registerOutParameter(String parameterName, int sqlType, int scale) throws SQLException {

  }

  @Override
  void registerOutParameter(String parameterName, int sqlType, String typeName) throws SQLException {

  }

  @Override
  URL getURL(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  void setURL(String parameterName, URL val) throws SQLException {

  }

  @Override
  void setNull(String parameterName, int sqlType) throws SQLException {

  }

  @Override
  void setBoolean(String parameterName, boolean x) throws SQLException {

  }

  @Override
  void setByte(String parameterName, byte x) throws SQLException {

  }

  @Override
  void setShort(String parameterName, short x) throws SQLException {

  }

  @Override
  void setInt(String parameterName, int x) throws SQLException {

  }

  @Override
  void setLong(String parameterName, long x) throws SQLException {

  }

  @Override
  void setFloat(String parameterName, float x) throws SQLException {

  }

  @Override
  void setDouble(String parameterName, double x) throws SQLException {

  }

  @Override
  void setBigDecimal(String parameterName, BigDecimal x) throws SQLException {

  }

  @Override
  void setString(String parameterName, String x) throws SQLException {

  }

  @Override
  void setBytes(String parameterName, byte[] x) throws SQLException {

  }

  @Override
  void setDate(String parameterName, Date x) throws SQLException {

  }

  @Override
  void setTime(String parameterName, Time x) throws SQLException {

  }

  @Override
  void setTimestamp(String parameterName, Timestamp x) throws SQLException {

  }

  @Override
  void setAsciiStream(String parameterName, InputStream x, int length) throws SQLException {

  }

  @Override
  void setBinaryStream(String parameterName, InputStream x, int length) throws SQLException {

  }

  @Override
  void setObject(String parameterName, Object x, int targetSqlType, int scale) throws SQLException {

  }

  @Override
  void setObject(String parameterName, Object x, int targetSqlType) throws SQLException {

  }

  @Override
  void setObject(String parameterName, Object x) throws SQLException {

  }

  @Override
  void setCharacterStream(String parameterName, Reader reader, int length) throws SQLException {

  }

  @Override
  void setDate(String parameterName, Date x, Calendar cal) throws SQLException {

  }

  @Override
  void setTime(String parameterName, Time x, Calendar cal) throws SQLException {

  }

  @Override
  void setTimestamp(String parameterName, Timestamp x, Calendar cal) throws SQLException {

  }

  @Override
  void setNull(String parameterName, int sqlType, String typeName) throws SQLException {

  }

  @Override
  String getString(String parameterName) throws SQLException {
    return null
  }

  @Override
  boolean getBoolean(String parameterName) throws SQLException {
    return false
  }

  @Override
  byte getByte(String parameterName) throws SQLException {
    return 0
  }

  @Override
  short getShort(String parameterName) throws SQLException {
    return 0
  }

  @Override
  int getInt(String parameterName) throws SQLException {
    return 0
  }

  @Override
  long getLong(String parameterName) throws SQLException {
    return 0
  }

  @Override
  float getFloat(String parameterName) throws SQLException {
    return 0
  }

  @Override
  double getDouble(String parameterName) throws SQLException {
    return 0
  }

  @Override
  byte[] getBytes(String parameterName) throws SQLException {
    return new byte[0]
  }

  @Override
  Date getDate(String parameterName) throws SQLException {
    return null
  }

  @Override
  Time getTime(String parameterName) throws SQLException {
    return null
  }

  @Override
  Timestamp getTimestamp(String parameterName) throws SQLException {
    return null
  }

  @Override
  Object getObject(String parameterName) throws SQLException {
    return null
  }

  @Override
  BigDecimal getBigDecimal(String parameterName) throws SQLException {
    return null
  }

  @Override
  Object getObject(String parameterName, Map<String, Class<?>> map) throws SQLException {
    return null
  }

  @Override
  Ref getRef(String parameterName) throws SQLException {
    return null
  }

  @Override
  Blob getBlob(String parameterName) throws SQLException {
    return null
  }

  @Override
  Clob getClob(String parameterName) throws SQLException {
    return null
  }

  @Override
  Array getArray(String parameterName) throws SQLException {
    return null
  }

  @Override
  Date getDate(String parameterName, Calendar cal) throws SQLException {
    return null
  }

  @Override
  Time getTime(String parameterName, Calendar cal) throws SQLException {
    return null
  }

  @Override
  Timestamp getTimestamp(String parameterName, Calendar cal) throws SQLException {
    return null
  }

  @Override
  URL getURL(String parameterName) throws SQLException {
    return null
  }

  @Override
  RowId getRowId(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  RowId getRowId(String parameterName) throws SQLException {
    return null
  }

  @Override
  void setRowId(String parameterName, RowId x) throws SQLException {

  }

  @Override
  void setNString(String parameterName, String value) throws SQLException {

  }

  @Override
  void setNCharacterStream(String parameterName, Reader value, long length) throws SQLException {

  }

  @Override
  void setNClob(String parameterName, NClob value) throws SQLException {

  }

  @Override
  void setClob(String parameterName, Reader reader, long length) throws SQLException {

  }

  @Override
  void setBlob(String parameterName, InputStream inputStream, long length) throws SQLException {

  }

  @Override
  void setNClob(String parameterName, Reader reader, long length) throws SQLException {

  }

  @Override
  NClob getNClob(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  NClob getNClob(String parameterName) throws SQLException {
    return null
  }

  @Override
  void setSQLXML(String parameterName, SQLXML xmlObject) throws SQLException {

  }

  @Override
  SQLXML getSQLXML(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  SQLXML getSQLXML(String parameterName) throws SQLException {
    return null
  }

  @Override
  String getNString(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  String getNString(String parameterName) throws SQLException {
    return null
  }

  @Override
  Reader getNCharacterStream(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  Reader getNCharacterStream(String parameterName) throws SQLException {
    return null
  }

  @Override
  Reader getCharacterStream(int parameterIndex) throws SQLException {
    return null
  }

  @Override
  Reader getCharacterStream(String parameterName) throws SQLException {
    return null
  }

  @Override
  void setBlob(String parameterName, Blob x) throws SQLException {

  }

  @Override
  void setClob(String parameterName, Clob x) throws SQLException {

  }

  @Override
  void setAsciiStream(String parameterName, InputStream x, long length) throws SQLException {

  }

  @Override
  void setBinaryStream(String parameterName, InputStream x, long length) throws SQLException {

  }

  @Override
  void setCharacterStream(String parameterName, Reader reader, long length) throws SQLException {

  }

  @Override
  void setAsciiStream(String parameterName, InputStream x) throws SQLException {

  }

  @Override
  void setBinaryStream(String parameterName, InputStream x) throws SQLException {

  }

  @Override
  void setCharacterStream(String parameterName, Reader reader) throws SQLException {

  }

  @Override
  void setNCharacterStream(String parameterName, Reader value) throws SQLException {

  }

  @Override
  void setClob(String parameterName, Reader reader) throws SQLException {

  }

  @Override
  void setBlob(String parameterName, InputStream inputStream) throws SQLException {

  }

  @Override
  void setNClob(String parameterName, Reader reader) throws SQLException {

  }

  @Override
  def <T> T getObject(int parameterIndex, Class<T> type) throws SQLException {
    return null
  }

  @Override
  def <T> T getObject(String parameterName, Class<T> type) throws SQLException {
    return null
  }
}
