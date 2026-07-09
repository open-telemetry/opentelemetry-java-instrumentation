/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedbcp;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import java.sql.Connection;
import java.sql.Driver;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractApacheDbcpInstrumentationTest {
  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.apache-dbcp-2.0";
  private static final AttributeKey<String> POOL_NAME_KEY =
      AttributeKey.stringKey(
          emitStableDatabaseSemconv() ? "db.client.connection.pool.name" : "pool.name");

  @Mock private Driver driverMock;
  @Mock private Connection connectionMock;

  protected abstract InstrumentationExtension testing();

  protected abstract void configure(BasicDataSource dataSource, String dataSourceName)
      throws Exception;

  protected abstract void shutdown(BasicDataSource dataSource) throws Exception;

  @Test
  void shouldReportMetrics() throws Exception {
    String dataSourceName = "dataSourceName";
    BasicDataSource dataSource = createDataSource();
    try {
      configure(dataSource, dataSourceName);

      dataSource.getConnection().close();

      assertDataSourceMetrics(dataSourceName);
    } finally {
      dataSource.close();
      shutdown(dataSource);
    }

    assertNoMetrics();
  }

  protected BasicDataSource createDataSource() throws Exception {
    when(driverMock.connect(any(), any())).thenReturn(connectionMock);
    when(connectionMock.isValid(anyInt())).thenReturn(true);

    BasicDataSource dataSource = new BasicDataSource();
    dataSource.setDriver(driverMock);
    dataSource.setUrl("db:///url");
    dataSource.postDeregister();
    return dataSource;
  }

  protected void assertDataSourceMetrics(String dataSourceName) {
    DbConnectionPoolMetricsAssertions.create(testing(), INSTRUMENTATION_NAME, dataSourceName)
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
        .disablePendingRequests()
        .assertConnectionPoolEmitsMetrics();
  }

  protected void assertConnectionUsagePoolNamesSatisfying(Consumer<Set<String>> assertion) {
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            emitStableDatabaseSemconv()
                ? "db.client.connection.count"
                : "db.client.connections.usage",
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertion.accept(
                            metric.getLongSumData().getPoints().stream()
                                .map(point -> point.getAttributes().get(POOL_NAME_KEY))
                                .collect(toSet()))));
  }

  protected void assertNoMetrics() {
    testing().clearData();

    await()
        .untilAsserted(
            () ->
                assertThat(testing().metrics())
                    .filteredOn(
                        metricData ->
                            metricData
                                .getInstrumentationScopeInfo()
                                .getName()
                                .equals(INSTRUMENTATION_NAME))
                    .isEmpty());
  }
}
