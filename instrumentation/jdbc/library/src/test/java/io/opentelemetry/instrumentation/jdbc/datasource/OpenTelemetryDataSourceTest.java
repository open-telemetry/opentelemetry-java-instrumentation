/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.instrumentation.jdbc.internal.OpenTelemetryConnection;
import io.opentelemetry.instrumentation.jdbc.internal.dbinfo.DbInfo;
import java.sql.Connection;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OpenTelemetryDataSourceTest {

  @DisplayName("verify get connection")
  @Test
  void verifyGetConnection() throws SQLException {

    OpenTelemetry openTelemetry = OpenTelemetry.propagating(ContextPropagators.noop());
    TestDataSource testDataSource = new TestDataSource();
    OpenTelemetryDataSource dataSource = new OpenTelemetryDataSource(testDataSource, openTelemetry);
    Connection connection = dataSource.getConnection();

    assertThat(connection).isExactlyInstanceOf(OpenTelemetryConnection.class);

    DbInfo dbInfo = ((OpenTelemetryConnection) connection).getDbInfo();

    assertThat(dbInfo.getSystem()).isEqualTo("postgresql");
    assertNull(dbInfo.getSubtype());
    assertThat(dbInfo.getShortUrl()).isEqualTo("postgresql://127.0.0.1:5432");
    assertNull(dbInfo.getUser());
    assertNull(dbInfo.getName());
    assertThat(dbInfo.getDb()).isEqualTo("dbname");
    assertThat(dbInfo.getHost()).isEqualTo("127.0.0.1");
    assertThat(dbInfo.getPort()).isEqualTo(5432);
  }

  @DisplayName("verify get connection with username and password")
  @Test
  void verifyGetConnectionWithUserNameAndPassword() throws SQLException {

    OpenTelemetry openTelemetry = OpenTelemetry.propagating(ContextPropagators.noop());
    OpenTelemetryDataSource dataSource =
        new OpenTelemetryDataSource(new TestDataSource(), openTelemetry);
    Connection connection = dataSource.getConnection(null, null);

    assertThat(connection).isExactlyInstanceOf(OpenTelemetryConnection.class);

    DbInfo dbInfo = ((OpenTelemetryConnection) connection).getDbInfo();

    assertThat(dbInfo.getSystem()).isEqualTo("postgresql");
    assertNull(dbInfo.getSubtype());
    assertThat(dbInfo.getShortUrl()).isEqualTo("postgresql://127.0.0.1:5432");
    assertNull(dbInfo.getUser());
    assertNull(dbInfo.getName());
    assertThat(dbInfo.getDb()).isEqualTo("dbname");
    assertThat(dbInfo.getHost()).isEqualTo("127.0.0.1");
    assertThat(dbInfo.getPort()).isEqualTo(5432);
  }
}
