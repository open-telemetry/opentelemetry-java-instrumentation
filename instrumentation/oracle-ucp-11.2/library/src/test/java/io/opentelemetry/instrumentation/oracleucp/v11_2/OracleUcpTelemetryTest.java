/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oracleucp.v11_2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import io.opentelemetry.instrumentation.testing.junit.db.MockDriver;
import oracle.ucp.UniversalConnectionPool;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import oracle.ucp.jdbc.PoolDataSourceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class OracleUcpTelemetryTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.oracle-ucp-11.2";

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static OracleUcpTelemetry telemetry;

  @BeforeAll
  static void setUp() {
    telemetry = OracleUcpTelemetry.create(testing.getOpenTelemetry());
  }

  @Test
  void shouldUseMetricPoolNameOverrideWithoutChangingUcpName() throws Exception {
    PoolDataSourceImpl dataSource = (PoolDataSourceImpl) PoolDataSourceFactory.getPoolDataSource();
    dataSource.setConnectionFactoryClassName(MockDriver.class.getName());
    dataSource.setURL("jdbc:mock:testDatabase");
    UniversalConnectionPool connectionPool = dataSource.createUniversalConnectionPool();
    String ucpPoolName = connectionPool.getName();

    try {
      telemetry.registerMetrics(connectionPool, "ordersPool");

      DbConnectionPoolMetricsAssertions.create(testing, INSTRUMENTATION_NAME, "ordersPool")
          .disableMinIdleConnections()
          .disableMaxIdleConnections()
          .disableConnectionTimeouts()
          .disableCreateTime()
          .disableWaitTime()
          .disableUseTime()
          .assertConnectionPoolEmitsMetrics();
      assertThat(connectionPool.getName()).isEqualTo(ucpPoolName);
    } finally {
      telemetry.unregisterMetrics(connectionPool);
      connectionPool.stop();
    }

    testing.clearData();
    await()
        .untilAsserted(
            () ->
                assertThat(testing.metrics())
                    .filteredOn(
                        metric ->
                            metric
                                .getInstrumentationScopeInfo()
                                .getName()
                                .equals(INSTRUMENTATION_NAME))
                    .isEmpty());
  }
}
