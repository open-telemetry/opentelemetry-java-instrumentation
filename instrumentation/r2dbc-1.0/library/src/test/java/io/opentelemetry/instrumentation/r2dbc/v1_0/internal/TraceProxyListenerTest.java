/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.r2dbc.v1_0.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.proxy.test.MockConnectionInfo;
import io.r2dbc.proxy.test.MockQueryExecutionInfo;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionMetadata;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TraceProxyListenerTest {

  @Test
  @SuppressWarnings("deprecation") // testing deprecated semconv
  void sharesConfigurationSnapshotAcrossExecutions() {
    Connection firstConnection = connection("firstdb");
    Connection secondConnection = connection("seconddb");
    QueryExecutionInfo firstQuery = queryExecutionInfo(firstConnection, "SELECT first");
    QueryExecutionInfo secondQuery = queryExecutionInfo(secondConnection, "SELECT second");
    Context firstContext = mock(Context.class);
    Context secondContext = mock(Context.class);
    @SuppressWarnings("unchecked")
    Instrumenter<DbExecution, Void> instrumenter = mock(Instrumenter.class);
    when(instrumenter.shouldStart(any(), any())).thenReturn(true);
    when(instrumenter.start(any(), any())).thenReturn(firstContext, secondContext);
    TraceProxyListener listener =
        new TraceProxyListener(
            instrumenter,
            ConnectionFactoryOptions.parse("r2dbc:pool:clickhouse:http://dbhost:8123/mydb"));

    listener.beforeQuery(firstQuery);
    listener.beforeQuery(secondQuery);

    ArgumentCaptor<DbExecution> executionCaptor = ArgumentCaptor.forClass(DbExecution.class);
    verify(instrumenter, times(2)).start(any(), executionCaptor.capture());
    DbExecution firstExecution = executionCaptor.getAllValues().get(0);
    DbExecution secondExecution = executionCaptor.getAllValues().get(1);
    assertThat(firstExecution).isNotSameAs(secondExecution);
    assertThat(firstExecution.connectionInfo()).isSameAs(secondExecution.connectionInfo());
    assertThat(firstExecution.getSystem()).isEqualTo("firstdb");
    assertThat(secondExecution.getSystem()).isEqualTo("seconddb");
    assertThat(firstExecution.getRawQueryTexts()).containsExactly("SELECT first");
    assertThat(secondExecution.getRawQueryTexts()).containsExactly("SELECT second");
    assertThat(firstExecution.getContext()).isSameAs(firstContext);
    assertThat(secondExecution.getContext()).isSameAs(secondContext);
    assertThat(firstExecution.getSystemName()).isEqualTo("other_sql");
    assertThat(secondExecution.getConfiguredServerAddress()).isEqualTo("dbhost");
    assertThat(secondExecution.getConfiguredServerPort()).isNull();
  }

  private static Connection connection(String databaseProductName) {
    ConnectionMetadata metadata = mock(ConnectionMetadata.class);
    when(metadata.getDatabaseProductName()).thenReturn(databaseProductName);
    Connection connection = mock(Connection.class);
    when(connection.getMetadata()).thenReturn(metadata);
    return connection;
  }

  private static QueryExecutionInfo queryExecutionInfo(Connection connection, String query) {
    return MockQueryExecutionInfo.builder()
        .queryInfo(new QueryInfo(query))
        .connectionInfo(MockConnectionInfo.builder().originalConnection(connection).build())
        .build();
  }
}
