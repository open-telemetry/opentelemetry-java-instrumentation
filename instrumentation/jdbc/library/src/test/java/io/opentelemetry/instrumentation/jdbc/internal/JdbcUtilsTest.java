/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class JdbcUtilsTest {

  @Test
  void cachedConnectionUsesConfiguredTarget() throws SQLException {
    Connection connection = mock(Connection.class);
    DbInfo configured =
        JdbcConnectionUrlParser.parse(
            "jdbc:mysql:replication://primary:3306,replica:3306/shop", null);
    JdbcData.CONNECTION_INFO.set(connection, configured);

    assertThat(request(connection).getDbInfo()).isSameAs(configured);
    assertThat(DbRequest.createTransaction(connection, "COMMIT").getDbInfo()).isSameAs(configured);
    assertThat(configured.getConfiguredServerTarget().getAddress()).isEqualTo("primary,replica");
    assertThat(configured.getConfiguredServerTarget().getPort()).isNull();
    assertThat(configured.getDbNamespace()).isEqualTo("shop");
    verify(connection, never()).getMetaData();
  }

  @Test
  void uncachedWrapperUsesCachedDelegate() throws SQLException {
    Connection wrapper = mock(Connection.class);
    Connection delegate = mock(Connection.class);
    when(wrapper.isWrapperFor(Connection.class)).thenReturn(true);
    when(wrapper.unwrap(Connection.class)).thenReturn(delegate);
    DbInfo configured = JdbcConnectionUrlParser.parse("jdbc:mysql://configured:3306/shop", null);
    JdbcData.CONNECTION_INFO.set(delegate, configured);

    assertThat(request(wrapper).getDbInfo()).isSameAs(configured);
    verify(wrapper, never()).getMetaData();
    verify(delegate, never()).getMetaData();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void uncachedConnectionUsesMetadataAndCachesOnDelegate(boolean wrapped) throws SQLException {
    Connection delegate = mock(Connection.class);
    DatabaseMetaData metadata = mock(DatabaseMetaData.class);
    when(delegate.getMetaData()).thenReturn(metadata);
    when(metadata.getURL()).thenReturn("jdbc:mysql://metadata:3306/shop");
    Connection connection = delegate;
    if (wrapped) {
      connection = mock(Connection.class);
      when(connection.isWrapperFor(Connection.class)).thenReturn(true);
      when(connection.unwrap(Connection.class)).thenReturn(delegate);
    }

    DbInfo dbInfo = request(connection).getDbInfo();
    assertThat(dbInfo)
        .isEqualTo(JdbcConnectionUrlParser.parse("jdbc:mysql://metadata:3306/shop", null));
    assertThat(request(connection).getDbInfo()).isEqualTo(dbInfo);
    assertThat(JdbcData.CONNECTION_INFO.get(delegate)).isEqualTo(dbInfo);
    if (wrapped) {
      assertThat(JdbcData.CONNECTION_INFO.get(connection)).isNull();
      verify(connection, never()).getMetaData();
    }
    verify(delegate).getMetaData();
  }

  @Test
  void unsupportedUnwrapStillUsesOriginalMetadata() throws SQLException {
    Connection connection = mock(Connection.class);
    when(connection.isWrapperFor(Connection.class)).thenReturn(true);
    when(connection.unwrap(Connection.class)).thenThrow(new SQLException("Unsupported"));
    DatabaseMetaData metadata = mock(DatabaseMetaData.class);
    when(connection.getMetaData()).thenReturn(metadata);
    when(metadata.getURL()).thenReturn("jdbc:mysql://metadata/shop");

    assertThat(request(connection).getDbInfo())
        .isEqualTo(JdbcConnectionUrlParser.parse("jdbc:mysql://metadata/shop", null));
  }

  private static DbRequest request(Connection connection) throws SQLException {
    Statement statement = mock(Statement.class);
    when(statement.getConnection()).thenReturn(connection);
    return DbRequest.create(statement, "SELECT 1");
  }
}
