/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DbExecutionTest {

  @Mock Connection connection;
  @Mock ConnectionMetadata metadata;

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
    assertEquals("testdb", dbExecution.getSystem());
    assertEquals("root", dbExecution.getUser());
    assertEquals("db", dbExecution.getName());
    assertEquals("localhost", dbExecution.getHost());
    assertEquals(3306, dbExecution.getPort());
    assertEquals("mariadb://localhost:3306", dbExecution.getConnectionString());
    assertEquals("SELECT * from person where last_name = 'tom'", dbExecution.getRawQueryText());
  }
}
