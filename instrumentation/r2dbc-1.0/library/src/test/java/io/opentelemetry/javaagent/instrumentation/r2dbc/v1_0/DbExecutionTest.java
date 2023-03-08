/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.internal.DbExecution;
import io.r2dbc.proxy.core.QueryExecutionInfo;
import io.r2dbc.proxy.core.QueryInfo;
import io.r2dbc.proxy.test.MockConnectionInfo;
import io.r2dbc.proxy.test.MockQueryExecutionInfo;
import io.r2dbc.spi.Batch;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.ConnectionMetadata;
import io.r2dbc.spi.IsolationLevel;
import io.r2dbc.spi.Statement;
import io.r2dbc.spi.TransactionDefinition;
import io.r2dbc.spi.ValidationDepth;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

class DbExecutionTest {

  @Test
  void dbExecution() {
    QueryExecutionInfo queryExecutionInfo =
        MockQueryExecutionInfo.builder()
            .queryInfo(new QueryInfo("SELECT * from person where last_name = 'tom'"))
            .connectionInfo(MockConnectionInfo.builder().originalConnection(mockConnection).build())
            .build();
    ConnectionFactoryOptions factoryOptions =
        ConnectionFactoryOptions.parse("r2dbc:mariadb://root:root@localhost:3306/db");
    io.opentelemetry.javaagent.instrumentation.r2dbc.v1_0.internal.DbExecution dbExecution =
        new DbExecution(queryExecutionInfo, factoryOptions);
    assertEquals("testdb", dbExecution.getSystem());
    assertEquals("root", dbExecution.getUser());
    assertEquals("db", dbExecution.getName());
    assertEquals("localhost", dbExecution.getHost());
    assertEquals(3306, dbExecution.getPort());
    assertEquals("mariadb://localhost:3306", dbExecution.getConnectionString());
    assertEquals("SELECT * from person where last_name = 'tom'", dbExecution.getRawStatement());
  }

  private static final Connection mockConnection =
      new Connection() {
        @Override
        public Publisher<Void> beginTransaction() {
          return null;
        }

        @Override
        public Publisher<Void> beginTransaction(TransactionDefinition definition) {
          return null;
        }

        @Override
        public Publisher<Void> close() {
          return null;
        }

        @Override
        public Publisher<Void> commitTransaction() {
          return null;
        }

        @Override
        public Batch createBatch() {
          return null;
        }

        @Override
        public Publisher<Void> createSavepoint(String name) {
          return null;
        }

        @Override
        public Statement createStatement(String sql) {
          return null;
        }

        @Override
        public boolean isAutoCommit() {
          return false;
        }

        @Override
        public ConnectionMetadata getMetadata() {
          return new ConnectionMetadata() {
            @Override
            public String getDatabaseProductName() {
              return "testdb";
            }

            @Override
            public String getDatabaseVersion() {
              return null;
            }
          };
        }

        @Override
        public IsolationLevel getTransactionIsolationLevel() {
          return null;
        }

        @Override
        public Publisher<Void> releaseSavepoint(String name) {
          return null;
        }

        @Override
        public Publisher<Void> rollbackTransaction() {
          return null;
        }

        @Override
        public Publisher<Void> rollbackTransactionToSavepoint(String name) {
          return null;
        }

        @Override
        public Publisher<Void> setAutoCommit(boolean autoCommit) {
          return null;
        }

        @Override
        public Publisher<Void> setLockWaitTimeout(Duration timeout) {
          return null;
        }

        @Override
        public Publisher<Void> setStatementTimeout(Duration timeout) {
          return null;
        }

        @Override
        public Publisher<Void> setTransactionIsolationLevel(IsolationLevel isolationLevel) {
          return null;
        }

        @Override
        public Publisher<Boolean> validate(ValidationDepth depth) {
          return null;
        }
      };
}
