/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package test

import java.sql.Array
import java.sql.Blob
import java.sql.Clob
import java.sql.Connection
import java.sql.Date
import java.sql.NClob
import java.sql.ParameterMetaData
import java.sql.PreparedStatement
import java.sql.Ref
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.RowId
import java.sql.SQLException
import java.sql.SQLXML
import java.sql.Time
import java.sql.Timestamp

class TestPreparedStatement extends TestStatement implements PreparedStatement {
  TestPreparedStatement(Connection connection) {
    super(connection)
  }

  @Override
  ResultSet executeQuery() throws SQLException {
    return null
  }

  @Override
  int executeUpdate() throws SQLException {
    return 0
  }

  @Override
  void setNull(int parameterIndex, int sqlType) throws SQLException {

  }

  @Override
  void setBoolean(int parameterIndex, boolean x) throws SQLException {

  }

  @Override
  void setByte(int parameterIndex, byte x) throws SQLException {

  }

  @Override
  void setShort(int parameterIndex, short x) throws SQLException {

  }

  @Override
  void setInt(int parameterIndex, int x) throws SQLException {

  }

  @Override
  void setLong(int parameterIndex, long x) throws SQLException {

  }

  @Override
  void setFloat(int parameterIndex, float x) throws SQLException {

  }

  @Override
  void setDouble(int parameterIndex, double x) throws SQLException {

  }

  @Override
  void setBigDecimal(int parameterIndex, BigDecimal x) throws SQLException {

  }

  @Override
  void setString(int parameterIndex, String x) throws SQLException {

  }

  @Override
  void setBytes(int parameterIndex, byte[] x) throws SQLException {

  }

  @Override
  void setDate(int parameterIndex, Date x) throws SQLException {

  }

  @Override
  void setTime(int parameterIndex, Time x) throws SQLException {

  }

  @Override
  void setTimestamp(int parameterIndex, Timestamp x) throws SQLException {

  }

  @Override
  void setAsciiStream(int parameterIndex, InputStream x, int length) throws SQLException {

  }

  @Override
  void setUnicodeStream(int parameterIndex, InputStream x, int length) throws SQLException {

  }

  @Override
  void setBinaryStream(int parameterIndex, InputStream x, int length) throws SQLException {

  }

  @Override
  void clearParameters() throws SQLException {

  }

  @Override
  void setObject(int parameterIndex, Object x, int targetSqlType) throws SQLException {

  }

  @Override
  void setObject(int parameterIndex, Object x) throws SQLException {

  }

  @Override
  boolean execute() throws SQLException {
    return false
  }

  @Override
  void addBatch() throws SQLException {

  }

  @Override
  void setCharacterStream(int parameterIndex, Reader reader, int length) throws SQLException {

  }

  @Override
  void setRef(int parameterIndex, Ref x) throws SQLException {

  }

  @Override
  void setBlob(int parameterIndex, Blob x) throws SQLException {

  }

  @Override
  void setClob(int parameterIndex, Clob x) throws SQLException {

  }

  @Override
  void setArray(int parameterIndex, Array x) throws SQLException {

  }

  @Override
  ResultSetMetaData getMetaData() throws SQLException {
    return null
  }

  @Override
  void setDate(int parameterIndex, Date x, Calendar cal) throws SQLException {

  }

  @Override
  void setTime(int parameterIndex, Time x, Calendar cal) throws SQLException {

  }

  @Override
  void setTimestamp(int parameterIndex, Timestamp x, Calendar cal) throws SQLException {

  }

  @Override
  void setNull(int parameterIndex, int sqlType, String typeName) throws SQLException {

  }

  @Override
  void setURL(int parameterIndex, URL x) throws SQLException {

  }

  @Override
  ParameterMetaData getParameterMetaData() throws SQLException {
    return null
  }

  @Override
  void setRowId(int parameterIndex, RowId x) throws SQLException {

  }

  @Override
  void setNString(int parameterIndex, String value) throws SQLException {

  }

  @Override
  void setNCharacterStream(int parameterIndex, Reader value, long length) throws SQLException {

  }

  @Override
  void setNClob(int parameterIndex, NClob value) throws SQLException {

  }

  @Override
  void setClob(int parameterIndex, Reader reader, long length) throws SQLException {

  }

  @Override
  void setBlob(int parameterIndex, InputStream inputStream, long length) throws SQLException {

  }

  @Override
  void setNClob(int parameterIndex, Reader reader, long length) throws SQLException {

  }

  @Override
  void setSQLXML(int parameterIndex, SQLXML xmlObject) throws SQLException {

  }

  @Override
  void setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength) throws SQLException {

  }

  @Override
  void setAsciiStream(int parameterIndex, InputStream x, long length) throws SQLException {

  }

  @Override
  void setBinaryStream(int parameterIndex, InputStream x, long length) throws SQLException {

  }

  @Override
  void setCharacterStream(int parameterIndex, Reader reader, long length) throws SQLException {

  }

  @Override
  void setAsciiStream(int parameterIndex, InputStream x) throws SQLException {

  }

  @Override
  void setBinaryStream(int parameterIndex, InputStream x) throws SQLException {

  }

  @Override
  void setCharacterStream(int parameterIndex, Reader reader) throws SQLException {

  }

  @Override
  void setNCharacterStream(int parameterIndex, Reader value) throws SQLException {

  }

  @Override
  void setClob(int parameterIndex, Reader reader) throws SQLException {

  }

  @Override
  void setBlob(int parameterIndex, InputStream inputStream) throws SQLException {

  }

  @Override
  void setNClob(int parameterIndex, Reader reader) throws SQLException {

  }
}
