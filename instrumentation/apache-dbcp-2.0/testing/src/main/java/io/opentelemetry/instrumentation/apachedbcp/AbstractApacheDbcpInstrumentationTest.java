/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedbcp;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import java.sql.Connection;
import java.sql.Driver;
import org.apache.commons.dbcp2.BasicDataSource;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractApacheDbcpInstrumentationTest {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-dbcp-2.0";

  @Mock private Driver driverMock;
  @Mock private Connection connectionMock;

  protected abstract InstrumentationExtension testing();

  protected abstract void configure(BasicDataSource dataSource, String dataSourceName)
      throws Exception;

  protected abstract void shutdown(BasicDataSource dataSource) throws Exception;

  @Test
  void shouldReportMetrics() throws Exception {
    // given
    when(driverMock.connect(any(), any())).thenReturn(connectionMock);
    when(connectionMock.isValid(anyInt())).thenReturn(true);

    String dataSourceName = "dataSourceName";
    BasicDataSource dataSource = new BasicDataSource();
    dataSource.setDriver(driverMock);
    dataSource.setUrl("db:///url");
    dataSource.postDeregister();
    configure(dataSource, dataSourceName);

    // when
    dataSource.getConnection().close();

    // then
    DbConnectionPoolMetricsAssertions.create(testing(), INSTRUMENTATION_NAME, dataSourceName)
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
        .disablePendingRequests()
        .assertConnectionPoolEmitsMetrics();

    // when
    dataSource.close();
    shutdown(dataSource);

    testing().clearData();

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            emitStableDatabaseSemconv()
                ? "db.client.connection.count"
                : "db.client.connections.usage",
            AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            emitStableDatabaseSemconv()
                ? "db.client.connection.idle.min"
                : "db.client.connections.idle.min",
            AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            emitStableDatabaseSemconv()
                ? "db.client.connection.idle.max"
                : "db.client.connections.idle.max",
            AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            emitStableDatabaseSemconv() ? "db.client.connection.max" : "db.client.connections.max",
            AbstractIterableAssert::isEmpty);
  }
}
