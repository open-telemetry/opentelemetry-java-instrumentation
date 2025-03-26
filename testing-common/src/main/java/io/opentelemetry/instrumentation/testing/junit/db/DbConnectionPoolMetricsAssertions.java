/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.db;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.LongSumAssert;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;

public final class DbConnectionPoolMetricsAssertions {

  private static final AttributeKey<String> POOL_NAME_KEY = stringKey("pool.name");
  private static final AttributeKey<String> STATE_KEY = stringKey("state");

  public static DbConnectionPoolMetricsAssertions create(
      InstrumentationExtension testing, String instrumentationName, String poolName) {
    return new DbConnectionPoolMetricsAssertions(testing, instrumentationName, poolName);
  }

  private final InstrumentationExtension testing;
  private final String instrumentationName;
  private final String poolName;

  private boolean testMinIdleConnections = true;
  private boolean testMaxIdleConnections = true;
  private boolean testMaxConnections = true;
  private boolean testPendingRequests = true;
  private boolean testConnectionTimeouts = true;
  private boolean testCreateTime = true;
  private boolean testWaitTime = true;
  private boolean testUseTime = true;

  DbConnectionPoolMetricsAssertions(
      InstrumentationExtension testing, String instrumentationName, String poolName) {
    this.testing = testing;
    this.instrumentationName = instrumentationName;
    this.poolName = poolName;
  }

  @CanIgnoreReturnValue
  public DbConnectionPoolMetricsAssertions disableMinIdleConnections() {
    testMinIdleConnections = false;
    return this;
  }

  @CanIgnoreReturnValue
  public DbConnectionPoolMetricsAssertions disableMaxIdleConnections() {
    testMaxIdleConnections = false;
    return this;
  }

  @CanIgnoreReturnValue
  public DbConnectionPoolMetricsAssertions disableMaxConnections() {
    testMaxConnections = false;
    return this;
  }

  @CanIgnoreReturnValue
  public DbConnectionPoolMetricsAssertions disablePendingRequests() {
    testPendingRequests = false;
    return this;
  }

  @CanIgnoreReturnValue
  public DbConnectionPoolMetricsAssertions disableConnectionTimeouts() {
    testConnectionTimeouts = false;
    return this;
  }

  @CanIgnoreReturnValue
  public DbConnectionPoolMetricsAssertions disableCreateTime() {
    testCreateTime = false;
    return this;
  }

  @CanIgnoreReturnValue
  public DbConnectionPoolMetricsAssertions disableWaitTime() {
    testWaitTime = false;
    return this;
  }

  @CanIgnoreReturnValue
  public DbConnectionPoolMetricsAssertions disableUseTime() {
    testUseTime = false;
    return this;
  }

  public void assertConnectionPoolEmitsMetrics() {
    verifyConnectionUsage();
    if (testMinIdleConnections) {
      verifyMinIdleConnections();
    }
    if (testMaxIdleConnections) {
      verifyMaxIdleConnections();
    }
    if (testMaxConnections) {
      verifyMaxConnections();
    }
    if (testPendingRequests) {
      verifyPendingRequests();
    }
    if (testConnectionTimeouts) {
      verifyTimeouts();
    }
    if (testCreateTime) {
      verifyCreateTime();
    }
    if (testWaitTime) {
      verifyWaitTime();
    }
    if (testUseTime) {
      verifyUseTime();
    }
  }

  private void verifyConnectionUsage() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        emitStableDatabaseSemconv() ? "db.client.connection.count" : "db.client.connections.usage",
        metrics -> metrics.anySatisfy(this::verifyUsageMetric));
  }

  private void verifyUsageMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("{connections}")
        .hasDescription(
            "The number of connections that are currently in state described by the state attribute.")
        .hasLongSumSatisfying(
            sum ->
                sum.isNotMonotonic()
                    .hasPointsSatisfying(
                        point ->
                            point.hasAttributesSatisfying(
                                equalTo(POOL_NAME_KEY, poolName), equalTo(STATE_KEY, "idle")),
                        point ->
                            point.hasAttributesSatisfying(
                                equalTo(POOL_NAME_KEY, poolName), equalTo(STATE_KEY, "used"))));
  }

  private void verifyMaxConnections() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.max",
        metrics -> metrics.anySatisfy(this::verifyMaxConnectionsMetric));
  }

  private void verifyMaxConnectionsMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("{connections}")
        .hasDescription("The maximum number of open connections allowed.")
        .hasLongSumSatisfying(this::verifyPoolName);
  }

  private void verifyMinIdleConnections() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.idle.min",
        metrics -> metrics.anySatisfy(this::verifyMinIdleConnectionsMetric));
  }

  private void verifyMinIdleConnectionsMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("{connections}")
        .hasDescription("The minimum number of idle open connections allowed.")
        .hasLongSumSatisfying(this::verifyPoolName);
  }

  private void verifyMaxIdleConnections() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.idle.max",
        metrics -> metrics.anySatisfy(this::verifyMaxIdleConnectionsMetric));
  }

  private void verifyMaxIdleConnectionsMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("{connections}")
        .hasDescription("The maximum number of idle open connections allowed.")
        .hasLongSumSatisfying(this::verifyPoolName);
  }

  private void verifyPoolName(LongSumAssert sum) {
    sum.isNotMonotonic()
        .hasPointsSatisfying(point -> point.hasAttributes(Attributes.of(POOL_NAME_KEY, poolName)));
  }

  private void verifyPendingRequests() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.pending_requests",
        metrics -> metrics.anySatisfy(this::verifyPendingRequestsMetric));
  }

  private void verifyPendingRequestsMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("{requests}")
        .hasDescription(
            "The number of pending requests for an open connection, cumulative for the entire pool.")
        .hasLongSumSatisfying(this::verifyPoolName);
  }

  private void verifyTimeouts() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.timeouts",
        metrics -> metrics.anySatisfy(this::verifyTimeoutsMetric));
  }

  private void verifyTimeoutsMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("{timeouts}")
        .hasDescription(
            "The number of connection timeouts that have occurred trying to obtain a connection from the pool.")
        .hasLongSumSatisfying(
            sum ->
                sum.isMonotonic()
                    .hasPointsSatisfying(
                        point -> point.hasAttributes(Attributes.of(POOL_NAME_KEY, poolName))));
  }

  private void verifyCreateTime() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.create_time",
        metrics -> metrics.anySatisfy(this::verifyCreateTimeMetric));
  }

  private void verifyCreateTimeMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("ms")
        .hasDescription("The time it took to create a new connection.")
        .hasHistogramSatisfying(
            histogram ->
                histogram.hasPointsSatisfying(
                    point -> point.hasAttributes(Attributes.of(POOL_NAME_KEY, poolName))));
  }

  private void verifyWaitTime() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.wait_time",
        metrics -> metrics.anySatisfy(this::verifyWaitTimeMetric));
  }

  private void verifyWaitTimeMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("ms")
        .hasDescription("The time it took to obtain an open connection from the pool.")
        .hasHistogramSatisfying(
            histogram ->
                histogram.hasPointsSatisfying(
                    point -> point.hasAttributes(Attributes.of(POOL_NAME_KEY, poolName))));
  }

  private void verifyUseTime() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.use_time",
        metrics -> metrics.anySatisfy(this::verifyUseTimeMetric));
  }

  private MetricAssert verifyUseTimeMetric(MetricData metric) {
    return assertThat(metric)
        .hasUnit("ms")
        .hasDescription("The time between borrowing a connection and returning it to the pool.")
        .hasHistogramSatisfying(
            histogram ->
                histogram.hasPointsSatisfying(
                    point -> point.hasAttributes(Attributes.of(POOL_NAME_KEY, poolName))));
  }
}
