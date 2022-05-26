/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.db;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.testing.assertj.HistogramAssert;
import io.opentelemetry.sdk.testing.assertj.HistogramPointAssert;
import io.opentelemetry.sdk.testing.assertj.LongPointAssert;
import io.opentelemetry.sdk.testing.assertj.LongSumAssert;
import java.util.function.Consumer;
import org.assertj.core.api.ListAssert;

public final class DbConnectionPoolMetricsAssertions {

  public static final AttributeKey<String> POOL_NAME_KEY = stringKey("pool.name");
  public static final AttributeKey<String> STATE_KEY = stringKey("state");

  public static DbConnectionPoolMetricsAssertions create(
      InstrumentationExtension testing, String instrumentationName, String poolName) {
    return new DbConnectionPoolMetricsAssertions(testing, instrumentationName, poolName);
  }

  private final InstrumentationExtension testing;
  private final String instrumentationName;
  private final String poolName;

  private boolean testMinIdleConnections = true;
  private boolean testMaxIdleConnections = true;
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

  public DbConnectionPoolMetricsAssertions disableMinIdleConnections() {
    testMinIdleConnections = false;
    return this;
  }

  public DbConnectionPoolMetricsAssertions disableMaxIdleConnections() {
    testMaxIdleConnections = false;
    return this;
  }

  public DbConnectionPoolMetricsAssertions disablePendingRequests() {
    testPendingRequests = false;
    return this;
  }

  public DbConnectionPoolMetricsAssertions disableConnectionTimeouts() {
    testConnectionTimeouts = false;
    return this;
  }

  public DbConnectionPoolMetricsAssertions disableCreateTime() {
    testCreateTime = false;
    return this;
  }

  public DbConnectionPoolMetricsAssertions disableWaitTime() {
    testWaitTime = false;
    return this;
  }

  public DbConnectionPoolMetricsAssertions disableUseTime() {
    testUseTime = false;
    return this;
  }

  public void assertConnectionPoolEmitsMetrics() {
    waitAndAssertMetrics("db.client.connections.usage", this::verifyUsageMetrics);
    if (testMinIdleConnections) {
      waitAndAssertMetrics("db.client.connections.idle.min", this::verifyIdleMinMetrics);
    }
    if (testMaxIdleConnections) {
      waitAndAssertMetrics("db.client.connections.idle.max", this::verifyIdleMaxMetrics);
    }
    waitAndAssertMetrics("db.client.connections.max", this::verifyMaxConnectionsMetrics);

    if (testPendingRequests) {
      waitAndAssertMetrics(
          "db.client.connections.pending_requests", this::verifyConnectionsPendingMetrics);
    }
    if (testConnectionTimeouts) {
      waitAndAssertMetrics("db.client.connections.timeouts", this::verifyConnectionsTimeoutMetrics);
    }
    if (testCreateTime) {
      waitAndAssertMetrics(
          "db.client.connections.create_time", this::verifyConnectionsCreateTimeMetrics);
    }
    if (testWaitTime) {
      waitAndAssertMetrics(
          "db.client.connections.wait_time", this::verifyConnectionsWaitTimeMetrics);
    }
    if (testUseTime) {
      waitAndAssertMetrics("db.client.connections.use_time", this::verifyConnectionsUseTimeMetrics);
    }
  }

  private void waitAndAssertMetrics(
      String metricName, Consumer<ListAssert<MetricData>> assertions) {
    testing.waitAndAssertMetrics(instrumentationName, metricName, assertions);
  }

  private void verifyConnectionsUseTimeMetrics(ListAssert<MetricData> metrics) {
    metrics.anySatisfy(
        metric ->
            assertThat(metric)
                .hasUnit("ms")
                .hasDescription(
                    "The time between borrowing a connection and returning it to the pool.")
                .hasHistogramSatisfying(this::verifyHistogramPoolName));
  }

  private void verifyConnectionsWaitTimeMetrics(ListAssert<MetricData> metrics) {
    metrics.anySatisfy(this::verifyConnectionsWaitTimeMetric);
  }

  private void verifyConnectionsWaitTimeMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("ms")
        .hasDescription("The time it took to obtain an open connection from the pool.")
        .hasHistogramSatisfying(this::verifyHistogramPoolName);
  }

  private void verifyHistogramPoolName(HistogramAssert histogram) {
    histogram.hasPointsSatisfying(this::verifyPoolName);
  }

  private void verifyConnectionsCreateTimeMetrics(ListAssert<MetricData> metrics) {
    metrics.anySatisfy(this::verifyConnectionsCreateTimeMetric);
  }

  private void verifyConnectionsCreateTimeMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("ms")
        .hasDescription("The time it took to create a new connection.")
        .hasHistogramSatisfying(this::verifyHistogramPoolName);
  }

  private void verifyConnectionsTimeoutMetrics(ListAssert<MetricData> metrics) {
    metrics.anySatisfy(this::verifyConnectionsTimeoutMetric);
  }

  private void verifyConnectionsTimeoutMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("timeouts")
        .hasDescription(
            "The number of connection timeouts that have occurred trying to obtain a connection from the pool.")
        .hasLongSumSatisfying(sum -> sum.isMonotonic().hasPointsSatisfying(this::verifyPoolName));
  }

  private void verifyConnectionsPendingMetrics(ListAssert<MetricData> metrics) {
    metrics.anySatisfy(this::verifyConnectionsPendingMetric);
  }

  private void verifyConnectionsPendingMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("requests")
        .hasDescription(
            "The number of pending requests for an open connection, cumulative for the entire pool.")
        .hasLongSumSatisfying(this::verifyNotMonotonicSumAndPoolName);
  }

  private void verifyNotMonotonicSumAndPoolName(LongSumAssert sum) {
    sum.isNotMonotonic().hasPointsSatisfying(this::verifyPoolName);
  }

  private void verifyMaxConnectionsMetrics(ListAssert<MetricData> metrics) {
    metrics.anySatisfy(this::verifyMaxConnectionsMetric);
  }

  private void verifyMaxConnectionsMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("connections")
        .hasDescription("The maximum number of open connections allowed.")
        .hasLongSumSatisfying(this::verifyNotMonotonicSumAndPoolName);
  }

  private void verifyIdleMaxMetrics(ListAssert<MetricData> metrics) {
    metrics.anySatisfy(this::verifyIdleMaxMetric);
  }

  private void verifyIdleMaxMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("connections")
        .hasDescription("The maximum number of idle open connections allowed.")
        .hasLongSumSatisfying(this::verifyNotMonotonicSumAndPoolName);
  }

  private void verifyIdleMinMetrics(ListAssert<MetricData> metrics) {
    metrics.anySatisfy(this::verifyIdleMinMetric);
  }

  private void verifyIdleMinMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("connections")
        .hasDescription("The minimum number of idle open connections allowed.")
        .hasLongSumSatisfying(this::verifyNotMonotonicSumAndPoolName);
  }

  private void verifyUsageMetrics(ListAssert<MetricData> metrics) {
    metrics.anySatisfy(this::verifyUsageMetric);
  }

  private void verifyUsageMetric(MetricData metric) {
    assertThat(metric)
        .hasUnit("connections")
        .hasDescription(
            "The number of connections that are currently in state described by the state attribute.")
        .hasLongSumSatisfying(
            sum ->
                sum.isNotMonotonic()
                    .hasPointsSatisfying(
                        point ->
                            point.hasAttributes(
                                Attributes.of(POOL_NAME_KEY, poolName, STATE_KEY, "idle")),
                        point ->
                            point.hasAttributes(
                                Attributes.of(POOL_NAME_KEY, poolName, STATE_KEY, "used"))));
  }

  private void verifyPoolName(HistogramPointAssert point) {
    point.hasAttributes(Attributes.of(POOL_NAME_KEY, poolName));
  }

  private void verifyPoolName(LongPointAssert point) {
    point.hasAttributes(Attributes.of(POOL_NAME_KEY, poolName));
  }
}
