/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.viburdbcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Predicate;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.vibur.dbcp.ViburDBCPDataSource;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractViburInstrumentationTest {
  protected static final String INSTRUMENTATION_NAME = "io.opentelemetry.vibur-dbcp-11.0";

  @Mock protected DataSource dataSourceMock;
  @Mock protected Connection connectionMock;

  protected abstract InstrumentationExtension testing();

  protected abstract void configure(ViburDBCPDataSource viburDataSource);

  protected abstract void shutdown(ViburDBCPDataSource viburDataSource);

  @Test
  void shouldReportMetrics() throws SQLException {
    // given
    when(dataSourceMock.getConnection()).thenReturn(connectionMock);

    ViburDBCPDataSource viburDataSource = new ViburDBCPDataSource();
    viburDataSource.setExternalDataSource(dataSourceMock);
    viburDataSource.setName("testPool");
    configure(viburDataSource);
    viburDataSource.start();

    // when
    Connection viburConnection = viburDataSource.getConnection();

    // then
    assertConnectionPoolEmitsMetrics("testPool");

    // when
    viburConnection.close();

    // this one too shouldn't cause any problems when called more than once
    viburDataSource.close();
    viburDataSource.close();
    shutdown(viburDataSource);

    // then
    assertNoConnectionPoolMetrics();
  }

  protected void assertConnectionPoolEmitsMetrics(String poolName) {
    DbConnectionPoolMetricsAssertions.create(testing(), INSTRUMENTATION_NAME, poolName)
        .disableMinIdleConnections()
        .disableMaxIdleConnections()
        .disablePendingRequests()
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
        .assertConnectionPoolEmitsMetrics();
  }

  protected void assertNoConnectionPoolMetrics() {
    testing().clearData();

    Predicate<MetricData> isViburMetric =
        metric -> metric.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME);
    await()
        .untilAsserted(() -> assertThat(testing().metrics()).filteredOn(isViburMetric).isEmpty());
  }
}
