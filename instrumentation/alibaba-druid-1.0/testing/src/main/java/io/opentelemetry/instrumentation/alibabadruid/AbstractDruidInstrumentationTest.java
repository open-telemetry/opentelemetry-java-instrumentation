/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.alibabadruid;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.alibaba.druid.pool.DruidDataSource;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import io.opentelemetry.instrumentation.testing.junit.db.MockDriver;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractDruidInstrumentationTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.alibaba-druid-1.0";
  private static final AttributeKey<String> POOL_NAME_KEY =
      AttributeKey.stringKey(
          emitStableDatabaseSemconv() ? "db.client.connection.pool.name" : "pool.name");
  private static final String CONNECTION_USAGE_METRIC_NAME =
      emitStableDatabaseSemconv() ? "db.client.connection.count" : "db.client.connections.usage";

  protected abstract InstrumentationExtension testing();

  protected abstract void configure(DruidDataSource dataSource, String dataSourceName)
      throws Exception;

  protected abstract void shutdown(DruidDataSource dataSource) throws Exception;

  @BeforeAll
  static void setUpMocks() throws SQLException {
    MockDriver.register();
  }

  @Test
  void shouldReportMetrics() throws Exception {
    String name = "dataSourceName";
    DruidDataSource dataSource = createDataSource();

    try {
      configure(dataSource, name);

      DbConnectionPoolMetricsAssertions.create(testing(), INSTRUMENTATION_NAME, name)
          .disableConnectionTimeouts()
          .disableCreateTime()
          .disableWaitTime()
          .disableUseTime()
          .assertConnectionPoolEmitsMetrics();
    } finally {
      dataSource.close();
      shutdown(dataSource);
    }

    assertNoMetrics();
  }

  @Test
  void shouldMergeDuplicateDataSourceNames() throws Exception {
    DruidDataSource firstDataSource = createDataSource();
    DruidDataSource secondDataSource = createDataSource();

    try {
      configure(firstDataSource, "duplicatePool");
      configure(secondDataSource, "duplicatePool");

      assertConnectionUsagePoolNames("duplicatePool");
    } finally {
      firstDataSource.close();
      secondDataSource.close();
      shutdown(firstDataSource);
      shutdown(secondDataSource);
    }

    assertNoMetrics();
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

  protected static DruidDataSource createDataSource() {
    DruidDataSource dataSource = new DruidDataSource();
    dataSource.setDriverClassName(MockDriver.class.getName());
    dataSource.setUrl("db:///url");
    dataSource.setTestWhileIdle(false);
    return dataSource;
  }

  protected void assertConnectionUsagePoolNames(String... poolNames) {
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            CONNECTION_USAGE_METRIC_NAME,
            metrics ->
                metrics.anySatisfy(
                    metric ->
                        assertThat(
                                metric.getLongSumData().getPoints().stream()
                                    .map(point -> point.getAttributes().get(POOL_NAME_KEY))
                                    .collect(toSet()))
                            .containsExactlyInAnyOrder(poolNames)));
  }
}
