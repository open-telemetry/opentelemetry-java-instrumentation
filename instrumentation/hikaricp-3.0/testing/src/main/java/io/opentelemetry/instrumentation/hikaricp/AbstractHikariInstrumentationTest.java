/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.hikaricp;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.db.DbConnectionPoolMetricsAssertions;
import java.sql.Connection;
import java.sql.SQLException;
import javax.annotation.Nullable;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public abstract class AbstractHikariInstrumentationTest {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.hikaricp-3.0";
  private static final AttributeKey<String> POOL_NAME_KEY =
      AttributeKey.stringKey(
          emitStableDatabaseSemconv() ? "db.client.connection.pool.name" : "pool.name");
  private static final String CONNECTION_USAGE_METRIC_NAME =
      emitStableDatabaseSemconv() ? "db.client.connection.count" : "db.client.connections.usage";

  @RegisterExtension
  private static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  @Mock protected DataSource dataSourceMock;
  @Mock protected Connection connectionMock;
  @Mock private IMetricsTracker userMetricsMock;

  protected abstract InstrumentationExtension testing();

  protected abstract void configure(
      HikariConfig poolConfig, @Nullable MetricsTrackerFactory userTracker);

  @Test
  void shouldReportMetrics() throws SQLException, InterruptedException {
    // given
    when(dataSourceMock.getConnection()).thenReturn(connectionMock);

    HikariDataSource hikariDataSource = new HikariDataSource();
    hikariDataSource.setPoolName("testPool");
    hikariDataSource.setDataSource(dataSourceMock);
    configure(hikariDataSource, null);
    cleanup.deferCleanup(hikariDataSource);

    // when
    Connection hikariConnection = hikariDataSource.getConnection();
    MILLISECONDS.sleep(100);
    hikariConnection.close();

    // then
    DbConnectionPoolMetricsAssertions.create(testing(), INSTRUMENTATION_NAME, "testPool")
        .disableMaxIdleConnections()
        // no timeouts happen during this test
        .disableConnectionTimeouts()
        .assertConnectionPoolEmitsMetrics();

    // when
    hikariDataSource.close();

    assertNoConnectionPoolMetrics();
  }

  @Test
  void shouldNotBreakCustomUserMetrics() throws SQLException, InterruptedException {
    // given
    when(dataSourceMock.getConnection()).thenReturn(connectionMock);

    HikariConfig hikariConfig = new HikariConfig();
    hikariConfig.setPoolName("anotherTestPool");
    hikariConfig.setDataSource(dataSourceMock);
    configure(hikariConfig, (poolName, poolStats) -> userMetricsMock);

    HikariDataSource hikariDataSource = new HikariDataSource(hikariConfig);
    cleanup.deferCleanup(hikariDataSource);

    // when
    Connection hikariConnection = hikariDataSource.getConnection();
    MILLISECONDS.sleep(100);
    hikariConnection.close();

    // then
    DbConnectionPoolMetricsAssertions.create(testing(), INSTRUMENTATION_NAME, "anotherTestPool")
        .disableMaxIdleConnections()
        // no timeouts happen during this test
        .disableConnectionTimeouts()
        .assertConnectionPoolEmitsMetrics();

    verify(userMetricsMock, atLeastOnce()).recordConnectionCreatedMillis(anyLong());
    verify(userMetricsMock, atLeastOnce()).recordConnectionAcquiredNanos(anyLong());
    verify(userMetricsMock, atLeastOnce()).recordConnectionUsageMillis(anyLong());
  }

  @Test
  void shouldReportTimeouts() throws SQLException {
    // given
    when(dataSourceMock.getConnection())
        .then(
            invocation -> {
              MILLISECONDS.sleep(2_000);
              throw new SQLException("timed out!");
            });

    HikariDataSource hikariDataSource = new HikariDataSource();
    hikariDataSource.setPoolName("timingOutPool");
    hikariDataSource.setDataSource(dataSourceMock);
    hikariDataSource.setConnectionTimeout(250 /* millis */);
    // start the pool without initializing connections
    hikariDataSource.setInitializationFailTimeout(-1);
    configure(hikariDataSource, null);

    cleanup.deferCleanup(hikariDataSource);

    // when
    Exception thrown = catchException(hikariDataSource::getConnection);

    // then
    assertThat(thrown).isInstanceOf(SQLException.class);

    DbConnectionPoolMetricsAssertions.create(testing(), INSTRUMENTATION_NAME, "timingOutPool")
        .disableMaxIdleConnections()
        // the connection is not even acquired
        .disableUseTime()
        .assertConnectionPoolEmitsMetrics();
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

  protected void assertNoConnectionPoolMetrics() {
    testing().clearData();

    await()
        .untilAsserted(
            () ->
                assertThat(testing().metrics())
                    .filteredOn(
                        metric ->
                            metric
                                .getInstrumentationScopeInfo()
                                .getName()
                                .equals(INSTRUMENTATION_NAME))
                    .isEmpty());
  }
}
