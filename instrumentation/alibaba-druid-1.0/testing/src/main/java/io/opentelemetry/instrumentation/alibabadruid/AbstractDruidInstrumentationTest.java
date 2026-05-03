/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.alibabadruid;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;

import com.alibaba.druid.pool.DruidDataSource;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import io.opentelemetry.instrumentation.testing.junit.db.MockDriver;
import java.sql.SQLException;
import javax.management.ObjectName;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractDruidInstrumentationTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.alibaba-druid-1.0";

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
    DruidDataSource dataSource = new DruidDataSource();
    dataSource.setDriverClassName(MockDriver.class.getName());
    dataSource.setUrl("db:///url");
    dataSource.setTestWhileIdle(false);
    configure(dataSource, name);

    // then
    ObjectName objectName = new ObjectName("com.alibaba.druid:type=DruidDataSource,id=" + name);

    DbConnectionPoolMetricsAssertions.create(
            testing(),
            INSTRUMENTATION_NAME,
            objectName.getKeyProperty("type") + "-" + objectName.getKeyProperty("id"))
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
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
    testing()
        .waitAndAssertMetrics(
            INSTRUMENTATION_NAME,
            emitStableDatabaseSemconv()
                ? "db.client.connection.pending_requests"
                : "db.client.connections.pending_requests",
            AbstractIterableAssert::isEmpty);
  }
}
