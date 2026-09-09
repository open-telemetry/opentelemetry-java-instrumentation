/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.db;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static java.util.Arrays.asList;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.AbstractPointAssert;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import io.opentelemetry.sdk.testing.assertj.LongSumAssert;
import io.opentelemetry.sdk.testing.assertj.MetricAssert;
import java.util.ArrayList;
import java.util.List;

public class DbConnectionPoolMetricsAssertions {

  private static final AttributeKey<String> POOL_NAME_KEY =
      stringKey(emitStableDatabaseSemconv() ? "db.client.connection.pool.name" : "pool.name");
  private static final AttributeKey<String> STATE_KEY =
      stringKey(emitStableDatabaseSemconv() ? "db.client.connection.state" : "state");

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
   * Declares the database attributes that every metric point is expected to carry when stable
   * database semantic conventions are enabled. Declaring them makes each point assertion exact, so
   * an unexpected attribute fails the assertion. Under the old semantic conventions each point is
   * expected to carry only the pool name and the attributes that the metric itself defines.
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
        emitStableDatabaseSemconv() ? "db.client.connection.count" : "db.client.connections.usage",
        metrics -> metrics.anySatisfy(this::verifyUsageMetric));
  }

  private void verifyUsageMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit(emitStableDatabaseSemconv() ? "{connection}" : "{connections}")
        .hasDescription(
            "The number of connections that are currently in state described by the state attribute.")
        .hasLongSumSatisfying(
            sum ->
                sum.isNotMonotonic()
                    .hasPointsSatisfying(
                        point -> verifyPointAttributes(point, equalTo(STATE_KEY, "idle")),
                        point -> verifyPointAttributes(point, equalTo(STATE_KEY, "used"))));
  }

  private void verifyMaxConnections() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        emitStableDatabaseSemconv() ? "db.client.connection.max" : "db.client.connections.max",
        metrics -> metrics.anySatisfy(this::verifyMaxConnectionsMetric));
  }

  private void verifyMaxConnectionsMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit(emitStableDatabaseSemconv() ? "{connection}" : "{connections}")
        .hasDescription("The maximum number of open connections allowed.")
        .hasLongSumSatisfying(this::verifyPoolName);
  }

  private void verifyMinIdleConnections() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        emitStableDatabaseSemconv()
            ? "db.client.connection.idle.min"
            : "db.client.connections.idle.min",
        metrics -> metrics.anySatisfy(this::verifyMinIdleConnectionsMetric));
  }

  private void verifyMinIdleConnectionsMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit(emitStableDatabaseSemconv() ? "{connection}" : "{connections}")
        .hasDescription("The minimum number of idle open connections allowed.")
        .hasLongSumSatisfying(this::verifyPoolName);
  }

  private void verifyMaxIdleConnections() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        emitStableDatabaseSemconv()
            ? "db.client.connection.idle.max"
            : "db.client.connections.idle.max",
        metrics -> metrics.anySatisfy(this::verifyMaxIdleConnectionsMetric));
  }

  private void verifyMaxIdleConnectionsMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit(emitStableDatabaseSemconv() ? "{connection}" : "{connections}")
        .hasDescription("The maximum number of idle open connections allowed.")
        .hasLongSumSatisfying(this::verifyPoolName);
  }

  private void verifyPoolName(LongSumAssert sum) {
    sum.isNotMonotonic().hasPointsSatisfying(this::verifyPointAttributes);
  }

  private void verifyPointAttributes(AbstractPointAssert<?, ?> point) {
    verifyPointAttributes(point, new AttributeAssertion[0]);
  }

  private void verifyPointAttributes(
      AbstractPointAssert<?, ?> point, AttributeAssertion... extraAttributes) {
    List<AttributeAssertion> assertions = new ArrayList<>();
    assertions.add(equalTo(POOL_NAME_KEY, poolName));
    assertions.addAll(asList(extraAttributes));
    if (databaseAttributesDeclared) {
      if (emitStableDatabaseSemconv()) {
        assertions.addAll(databaseAttributes);
      }
      point.hasAttributesSatisfyingExactly(assertions.toArray(new AttributeAssertion[0]));
    } else {
      point.hasAttributesSatisfying(assertions.toArray(new AttributeAssertion[0]));
    }
  }

  private void verifyPendingRequests() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        emitStableDatabaseSemconv()
            ? "db.client.connection.pending_requests"
            : "db.client.connections.pending_requests",
        metrics -> metrics.anySatisfy(this::verifyPendingRequestsMetric));
  }

  private void verifyPendingRequestsMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit(emitStableDatabaseSemconv() ? "{request}" : "{requests}")
        .hasDescription(
            emitStableDatabaseSemconv()
                ? "The number of current pending requests for an open connection."
                : "The number of pending requests for an open connection, cumulative for the entire pool.")
        .hasLongSumSatisfying(this::verifyPoolName);
  }

  private void verifyTimeouts() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        emitStableDatabaseSemconv()
            ? "db.client.connection.timeouts"
            : "db.client.connections.timeouts",
        metrics -> metrics.anySatisfy(this::verifyTimeoutsMetric));
  }

  private void verifyTimeoutsMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit(emitStableDatabaseSemconv() ? "{timeout}" : "{timeouts}")
        .hasDescription(
            "The number of connection timeouts that have occurred trying to obtain a connection from the pool.")
        .hasLongSumSatisfying(
            sum -> sum.isMonotonic().hasPointsSatisfying(this::verifyPointAttributes));
  }

  private void verifyCreateTime() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        emitStableDatabaseSemconv()
            ? "db.client.connection.create_time"
            : "db.client.connections.create_time",
        metrics -> metrics.anySatisfy(this::verifyCreateTimeMetric));
  }

  private void verifyCreateTimeMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit(emitStableDatabaseSemconv() ? "s" : "ms")
        .hasDescription("The time it took to create a new connection.")
        .hasHistogramSatisfying(
            histogram -> histogram.hasPointsSatisfying(this::verifyPointAttributes));
  }

  private void verifyWaitTime() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        emitStableDatabaseSemconv()
            ? "db.client.connection.wait_time"
            : "db.client.connections.wait_time",
        metrics -> metrics.anySatisfy(this::verifyWaitTimeMetric));
  }

  private void verifyWaitTimeMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit(emitStableDatabaseSemconv() ? "s" : "ms")
        .hasDescription("The time it took to obtain an open connection from the pool.")
        .hasHistogramSatisfying(
            histogram -> histogram.hasPointsSatisfying(this::verifyPointAttributes));
  }

  private void verifyUseTime() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        emitStableDatabaseSemconv()
            ? "db.client.connection.use_time"
            : "db.client.connections.use_time",
        metrics -> metrics.anySatisfy(this::verifyUseTimeMetric));
  }

  private MetricAssert verifyUseTimeMetric(MetricData metric) {
    return assertThat(metric)
        .hasUnit(emitStableDatabaseSemconv() ? "s" : "ms")
        .hasDescription("The time between borrowing a connection and returning it to the pool.")
        .hasHistogramSatisfying(
            histogram -> histogram.hasPointsSatisfying(this::verifyPointAttributes));
  }
}
