/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.r2dbc.v1_0.internal.DbExecution;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.proxy.test.MockConnectionInfo;
import io.r2dbc.proxy.test.MockQueryExecutionInfo;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbExecutionTest {

  @Mock Connection connection;
  @Mock ConnectionMetadata metadata;

  @SuppressWarnings("deprecation") // testing deprecated semconv
  @Test
  void dbExecution() {
    when(connection.getMetadata()).thenReturn(metadata);
    when(metadata.getDatabaseProductName()).thenReturn("testdb");
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("SELECT * from person where last_name = 'tom'"))
            .connectionInfo(MockConnectionInfo.builder().originalConnection(connection).build())
            .build();
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.parse("r2dbc:mariadb://root:root@localhost:3306/db");
    DbExecution dbExecution = new DbExecution(queryExecutionInfo, factoryOptions);
    assertThat(dbExecution.getSystemName()).isEqualTo("mariadb");
    assertThat(dbExecution.getSystem()).isEqualTo("testdb");
    assertThat(dbExecution.getUser()).isEqualTo("root");
    assertThat(dbExecution.getNamespace()).isEqualTo("db");
    assertThat(dbExecution.getServerAddress()).isEqualTo("localhost");
    assertThat(dbExecution.getServerPort()).isEqualTo(3306);
    assertThat(dbExecution.getConnectionString()).isEqualTo("mariadb://localhost:3306");
    assertThat(dbExecution.getRawQueryText())
        .isEqualTo("SELECT * from person where last_name = 'tom'");
  }

  @SuppressWarnings("deprecation") // testing deprecated semconv
  @Test
  void dbExecutionWithPool() {
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("SELECT 1"))
            .connectionInfo(MockConnectionInfo.builder().build())
            .build();
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.parse("r2dbc:pool:postgresql://user:pass@dbhost:5432/mydb");
    DbExecution dbExecution = new DbExecution(queryExecutionInfo, factoryOptions);
    assertThat(dbExecution.getSystemName()).isEqualTo("postgresql");
    assertThat(dbExecution.getSystem()).isEqualTo("other_sql");
    assertThat(dbExecution.getUser()).isEqualTo("user");
    assertThat(dbExecution.getNamespace()).isEqualTo("mydb");
    assertThat(dbExecution.getServerAddress()).isEqualTo("dbhost");
    assertThat(dbExecution.getServerPort()).isEqualTo(5432);
    assertThat(dbExecution.getConnectionString()).isEqualTo("pool:postgresql://dbhost:5432");
  }

  @ParameterizedTest
  @CsvSource({
    "r2dbc:postgresql://localhost/db, postgresql",
    "r2dbc:mysql://localhost/db, mysql",
    "r2dbc:mariadb://localhost/db, mariadb",
    "r2dbc:mssql://localhost/db, microsoft.sql_server",
    "r2dbc:oracle://localhost/db, oracle.db",
    "r2dbc:h2:mem:///testdb, h2database",
    "r2dbc:unknown://localhost/db, other_sql",
  })
  void dbSystemName(String url, String expectedSystemName) {
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("SELECT 1"))
            .connectionInfo(MockConnectionInfo.builder().build())
            .build();
    ConnectionFactoryOptions factoryOptions = ConnectionFactoryOptions.parse(url);
    DbExecution dbExecution = new DbExecution(queryExecutionInfo, factoryOptions);
    assertThat(dbExecution.getSystemName()).isEqualTo(expectedSystemName);
  }
}
