/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.oracleucp;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import java.sql.Connection;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import oracle.ucp.admin.UniversalConnectionPoolManagerImpl;
import oracle.ucp.jdbc.PoolDataSource;
import oracle.ucp.jdbc.PoolDataSourceFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.oracle.OracleContainer;

public abstract class AbstractOracleUcpInstrumentationTest {
  private static final Logger logger =
      LoggerFactory.getLogger(AbstractOracleUcpInstrumentationTest.class);

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.orcale-ucp-11.2";
  private static OracleContainer oracle;

  protected abstract InstrumentationExtension testing();

  protected abstract void configure(PoolDataSource connectionPool) throws Exception;

  protected abstract void shutdown(PoolDataSource connectionPool) throws Exception;

  @BeforeAll
  static void setUp() {
    // This docker image does not work on arm mac. To run this test on arm mac read
    // https://blog.jdriven.com/2022/07/running-oracle-xe-with-testcontainers-on-apple-silicon/
    // install colima with brew install colima
    // colima start --arch x86_64 --memory 4
    // export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
    // export DOCKER_HOST="unix://${HOME}/.colima/docker.sock"
    String dockerHost = System.getenv("DOCKER_HOST");
    if (!"aarch64".equals(System.getProperty("os.arch"))
        || (dockerHost != null && dockerHost.contains("colima"))) {
      oracle =
          new OracleContainer("gvenzl/oracle-free:23.4-slim-faststart")
              .withLogConsumer(new Slf4jLogConsumer(logger))
              .withStartupTimeout(Duration.ofMinutes(2));
      oracle.start();
    }
  }

  @AfterAll
  static void cleanUp() {
    if (oracle != null) {
      oracle.stop();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldReportMetrics(boolean setExplicitPoolName) throws Exception {
    Assumptions.assumeTrue(oracle != null);

    // given
    PoolDataSource connectionPool = PoolDataSourceFactory.getPoolDataSource();
    connectionPool.setConnectionFactoryClassName("oracle.jdbc.pool.OracleDataSource");
    connectionPool.setURL(oracle.getJdbcUrl());
    connectionPool.setUser(oracle.getUsername());
    connectionPool.setPassword(oracle.getPassword());
    if (setExplicitPoolName) {
      connectionPool.setConnectionPoolName("testPool");
    }

    // when
    Connection connection = connectionPool.getConnection();
    configure(connectionPool);
    TimeUnit.MILLISECONDS.sleep(100);
    connection.close();

    // then
    DbConnectionPoolMetricsAssertions.create(
            testing(), INSTRUMENTATION_NAME, connectionPool.getConnectionPoolName())
        .disableMinIdleConnections()
        .disableMaxIdleConnections()
        .disableConnectionTimeouts()
        .disableCreateTime()
        .disableWaitTime()
        .disableUseTime()
        .assertConnectionPoolEmitsMetrics();

    // when
    // this one too shouldn't cause any problems when called more than once
    connectionPool.getConnection().close();
    connectionPool.getConnection().close();

    shutdown(connectionPool);
    UniversalConnectionPoolManagerImpl.getUniversalConnectionPoolManager()
        .destroyConnectionPool(connectionPool.getConnectionPoolName());

    // sleep exporter interval
    Thread.sleep(100);
    testing().clearData();
    Thread.sleep(100);

    // then
    Set<String> metricNames =
        new HashSet<>(
            Arrays.asList(
                emitStableDatabaseSemconv()
                    ? "db.client.connection.count"
                    : "db.client.connections.usage",
                "db.client.connections.max",
                "db.client.connections.pending_requests"));
    assertThat(testing().metrics())
        .filteredOn(
            metricData ->
                metricData.getInstrumentationScopeInfo().getName().equals(INSTRUMENTATION_NAME)
                    && metricNames.contains(metricData.getName()))
        .isEmpty();
  }
}
