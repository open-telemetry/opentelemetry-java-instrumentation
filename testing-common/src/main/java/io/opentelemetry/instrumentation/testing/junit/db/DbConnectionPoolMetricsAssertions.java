/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.db;

import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.histogram;
import static io.opentelemetry.instrumentation.testing.util.MetricAssertions.longSum;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CLIENT_CONNECTION_POOL_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_CLIENT_CONNECTION_STATE;
import static java.util.Arrays.asList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.AbstractPointAssert;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.util.ArrayList;
import java.util.List;

public class DbConnectionPoolMetricsAssertions {

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
  private boolean databaseAttributesDeclared;
  private final List<AttributeAssertion> databaseAttributes = new ArrayList<>();

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

  /**
   * Declares the database attributes that every metric point is expected to carry. Declaring them
   * makes each point assertion exact, so an unexpected attribute fails the assertion.
   */
  @CanIgnoreReturnValue
  public DbConnectionPoolMetricsAssertions withDatabaseAttributes(
      AttributeAssertion... assertions) {
    databaseAttributesDeclared = true;
    databaseAttributes.clear();
    databaseAttributes.addAll(asList(assertions));
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
        "db.client.connection.count",
        metrics -> metrics.anySatisfy(this::verifyUsageMetric));
  }

  private void verifyUsageMetric(MetricData metric) {
    longSum(
            metricAssert ->
                metricAssert
                    .hasUnit("{connection}")
                    .hasDescription(
                        "The number of connections that are currently in state described by the state attribute.")
                    .hasLongSumSatisfying(sum -> sum.isNotMonotonic()),
            point -> verifyPointAttributes(point, equalTo(DB_CLIENT_CONNECTION_STATE, "idle")),
            point -> verifyPointAttributes(point, equalTo(DB_CLIENT_CONNECTION_STATE, "used")))
        .accept(assertThat(metric));
  }

  private void verifyMaxConnections() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connection.limit",
        metrics -> metrics.anySatisfy(this::verifyMaxConnectionsMetric));
  }

  private void verifyMaxConnectionsMetric(MetricData metric) {
    longSum(
            metricAssert ->
                metricAssert
                    .hasUnit("{connection}")
                    .hasDescription("The maximum number of open connections allowed.")
                    .hasLongSumSatisfying(sum -> sum.isNotMonotonic()),
            this::verifyPointAttributes)
        .accept(assertThat(metric));
  }

  private void verifyMinIdleConnections() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connection.idle.min",
        metrics -> metrics.anySatisfy(this::verifyMinIdleConnectionsMetric));
  }

  private void verifyMinIdleConnectionsMetric(MetricData metric) {
    longSum(
            metricAssert ->
                metricAssert
                    .hasUnit("{connection}")
                    .hasDescription("The minimum number of idle open connections allowed.")
                    .hasLongSumSatisfying(sum -> sum.isNotMonotonic()),
            this::verifyPointAttributes)
        .accept(assertThat(metric));
  }

  private void verifyMaxIdleConnections() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connection.idle.max",
        metrics -> metrics.anySatisfy(this::verifyMaxIdleConnectionsMetric));
  }

  private void verifyMaxIdleConnectionsMetric(MetricData metric) {
    longSum(
            metricAssert ->
                metricAssert
                    .hasUnit("{connection}")
                    .hasDescription("The maximum number of idle open connections allowed.")
                    .hasLongSumSatisfying(sum -> sum.isNotMonotonic()),
            this::verifyPointAttributes)
        .accept(assertThat(metric));
  }

  private void verifyPointAttributes(AbstractPointAssert<?, ?> point) {
    verifyPointAttributes(point, new AttributeAssertion[0]);
  }

  private void verifyPointAttributes(
      AbstractPointAssert<?, ?> point, AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    assertions.add(equalTo(DB_CLIENT_CONNECTION_POOL_NAME, poolName));
    assertions.addAll(asList(extraAttributes));
    if (databaseAttributesDeclared) {
      assertions.addAll(databaseAttributes);
      point.hasAttributesSatisfyingExactly(assertions.toArray(new AttributeAssertion[0]));
    } else {
      point.hasAttributesSatisfying(assertions.toArray(new AttributeAssertion[0]));
    }
  }

  private void verifyPendingRequests() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connection.pending_requests",
        metrics -> metrics.anySatisfy(this::verifyPendingRequestsMetric));
  }

  private void verifyPendingRequestsMetric(MetricData metric) {
    longSum(
            metricAssert ->
                metricAssert
                    .hasUnit("{request}")
                    .hasDescription(
                        "The number of current pending requests for an open connection.")
                    .hasLongSumSatisfying(sum -> sum.isNotMonotonic()),
            this::verifyPointAttributes)
        .accept(assertThat(metric));
  }

  private void verifyTimeouts() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connection.timeouts",
        metrics -> metrics.anySatisfy(this::verifyTimeoutsMetric));
  }

  private void verifyTimeoutsMetric(MetricData metric) {
    longSum(
            metricAssert ->
                metricAssert
                    .hasUnit("{timeout}")
                    .hasDescription(
                        "The number of connection timeouts that have occurred trying to obtain a connection from the pool.")
                    .hasLongSumSatisfying(sum -> sum.isMonotonic()),
            this::verifyPointAttributes)
        .accept(assertThat(metric));
  }

  private void verifyCreateTime() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connection.create_time",
        metrics -> metrics.anySatisfy(this::verifyCreateTimeMetric));
  }

  private void verifyCreateTimeMetric(MetricData metric) {
    histogram(
            metricAssert ->
                metricAssert
                    .hasUnit("s")
                    .hasDescription("The time it took to create a new connection."),
            this::verifyPointAttributes)
        .accept(assertThat(metric));
  }

  private void verifyWaitTime() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connection.wait_time",
        metrics -> metrics.anySatisfy(this::verifyWaitTimeMetric));
  }

  private void verifyWaitTimeMetric(MetricData metric) {
    histogram(
            metricAssert ->
                metricAssert
                    .hasUnit("s")
                    .hasDescription("The time it took to obtain an open connection from the pool."),
            this::verifyPointAttributes)
        .accept(assertThat(metric));
  }

  private void verifyUseTime() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connection.use_time",
        metrics -> metrics.anySatisfy(this::verifyUseTimeMetric));
  }

  private void verifyUseTimeMetric(MetricData metric) {
    histogram(
            metricAssert ->
                metricAssert
                    .hasUnit("s")
                    .hasDescription(
                        "The time between borrowing a connection and returning it to the pool."),
            this::verifyPointAttributes)
        .accept(assertThat(metric));
  }
}
