/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.viburdbcp;

import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vibur.dbcp.ViburDBCPDataSource;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractViburInstrumentationTest {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.vibur-dbcp-11.0";

  @Mock DataSource dataSourceMock;
  @Mock Connection connectionMock;

  protected abstract InstrumentationExtension testing();

  protected abstract void configure(ViburDBCPDataSource viburDataSource);

  protected abstract void shutdown(ViburDBCPDataSource viburDataSource);

  @Test
  void shouldReportMetrics() throws SQLException, InterruptedException {
    // given
    when(dataSourceMock.getConnection()).thenReturn(connectionMock);

    ViburDBCPDataSource viburDataSource = new ViburDBCPDataSource();
    viburDataSource.setExternalDataSource(dataSourceMock);
    viburDataSource.setName("testPool");
    configure(viburDataSource);
    viburDataSource.start();

    // when
    Connection viburConnection = viburDataSource.getConnection();
    TimeUnit.MILLISECONDS.sleep(100);
    viburConnection.close();

    // then
    DbConnectionPoolMetricsAssertions.create(testing(), INSTRUMENTATION_NAME, "testPool")
        .disableMinIdleConnections()
        .disableMaxIdleConnections()
        .disablePendingRequests()
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
        .assertConnectionPoolEmitsMetrics();

    // when
    // this one too shouldn't cause any problems when called more than once
    viburDataSource.close();
    viburDataSource.close();
    shutdown(viburDataSource);

    // sleep exporter interval
    Thread.sleep(100);
    testing().clearData();
    Thread.sleep(100);

    // then
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "db.client.connections.usage", AbstractIterableAssert::isEmpty);
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME, "db.client.connections.max", AbstractIterableAssert::isEmpty);
  }
}
