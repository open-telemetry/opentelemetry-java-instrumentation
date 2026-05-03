/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.alibabadruid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.alibaba.druid.pool.DruidDataSource;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import io.opentelemetry.instrumentation.testing.junit.db.MockDriver;
import java.sql.SQLException;
import javax.management.ObjectName;
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
