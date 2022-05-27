/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.tomcat.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;

import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.assertj.core.api.AbstractIterableAssert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TomcatJdbcInstrumentationTest {

  //  @RegisterExtension
  //  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();
  @RegisterExtension
  static final AgentInstrumentationExtension testing = AgentInstrumentationExtension.create();

  //  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();
  @Mock javax.sql.DataSource dataSourceMock;
  @Mock Connection connectionMock;

  @Test
  void shouldReportMetrics() throws Exception {
    // given
    given(dataSourceMock.getConnection()).willReturn(connectionMock);

    DataSource tomcatDataSource = new DataSource();
    tomcatDataSource.setDataSource(dataSourceMock);

    // there shouldn't be any problems if this methods gets called more than once
    tomcatDataSource.createPool();
    tomcatDataSource.createPool();

    // when
    Connection connection = tomcatDataSource.getConnection();
    connection.close();

    // then
    await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(() -> assertConnectionPoolMetrics(tomcatDataSource.getPoolName()));

    // when
    // this one too shouldn't cause any problems when called more than once
    tomcatDataSource.close();
    tomcatDataSource.close();
    testing.clearData();

    // then
    await()
        .atMost(20, TimeUnit.SECONDS)
        .untilAsserted(TomcatJdbcInstrumentationTest::assertNoConnectionPoolMetrics);
  }

  private static void assertConnectionPoolMetrics(String poolName) {
    assertThat(poolName)
        .as("tomcat-jdbc generates a unique pool name if it's not explicitly provided")
        .isNotEmpty();

    DbConnectionPoolMetricsAssertions.create(testing, "io.opentelemetry.tomcat-jdbc", poolName)
        // no timeouts happen during this test
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
        .assertConnectionPoolEmitsMetrics();
  }

  private static void assertNoConnectionPoolMetrics() {
    testing.waitAndAssertMetrics(
        "io.opentelemetry.tomcat-jdbc",
        "db.client.connections.usage",
        AbstractIterableAssert::isEmpty);
    testing.waitAndAssertMetrics(
        "io.opentelemetry.tomcat-jdbc",
        "db.client.connections.idle.min",
        AbstractIterableAssert::isEmpty);
    testing.waitAndAssertMetrics(
        "io.opentelemetry.tomcat-jdbc",
        "db.client.connections.idle.max",
        AbstractIterableAssert::isEmpty);
    testing.waitAndAssertMetrics(
        "io.opentelemetry.tomcat-jdbc",
        "db.client.connections.max",
        AbstractIterableAssert::isEmpty);
    testing.waitAndAssertMetrics(
        "io.opentelemetry.tomcat-jdbc",
        "db.client.connections.pending_requests",
        AbstractIterableAssert::isEmpty);
  }
}
