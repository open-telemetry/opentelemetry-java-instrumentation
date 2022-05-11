/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.db;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;

public final class DbConnectionPoolMetricsAssertions {

  public static DbConnectionPoolMetricsAssertions create(
      InstrumentationExtension testing, String instrumentationName, String poolName) {
    return new DbConnectionPoolMetricsAssertions(testing, instrumentationName, poolName);
  }

  private final InstrumentationExtension testing;
  private final String instrumentationName;
  private final String poolName;

  private boolean testMaxIdleConnections = true;
  private boolean testConnectionTimeouts = true;

  DbConnectionPoolMetricsAssertions(
      InstrumentationExtension testing, String instrumentationName, String poolName) {
    this.testing = testing;
    this.instrumentationName = instrumentationName;
    this.poolName = poolName;
  }

  public DbConnectionPoolMetricsAssertions disableMaxIdleConnections() {
    testMaxIdleConnections = false;
    return this;
  }

  public DbConnectionPoolMetricsAssertions disableConnectionTimeouts() {
    testConnectionTimeouts = false;
    return this;
  }

  public void assertConnectionPoolEmitsMetrics() {
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.usage",
        metrics ->
            metrics.anySatisfy(
                metric ->
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
                                                Attributes.of(
                                                    stringKey("pool.name"),
                                                    poolName,
                                                    stringKey("state"),
                                                    "idle")),
                                        point ->
                                            point.hasAttributes(
                                                Attributes.of(
                                                    stringKey("pool.name"),
                                                    poolName,
                                                    stringKey("state"),
                                                    "used"))))));
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.idle.min",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasUnit("connections")
                        .hasDescription("The minimum number of idle open connections allowed.")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                Attributes.of(
                                                    stringKey("pool.name"), poolName))))));
    if (testMaxIdleConnections) {
      testing.waitAndAssertMetrics(
          instrumentationName,
          "db.client.connections.idle.max",
          metrics ->
              metrics.anySatisfy(
                  metric ->
                      assertThat(metric)
                          .hasUnit("connections")
                          .hasDescription("The maximum number of idle open connections allowed.")
                          .hasLongSumSatisfying(
                              sum ->
                                  sum.isNotMonotonic()
                                      .hasPointsSatisfying(
                                          point ->
                                              point.hasAttributes(
                                                  Attributes.of(
                                                      stringKey("pool.name"), poolName))))));
    }
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.max",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasUnit("connections")
                        .hasDescription("The maximum number of open connections allowed.")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                Attributes.of(
                                                    stringKey("pool.name"), poolName))))));
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.pending_requests",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasUnit("requests")
                        .hasDescription(
                            "The number of pending requests for an open connection, cumulative for the entire pool.")
                        .hasLongSumSatisfying(
                            sum ->
                                sum.isNotMonotonic()
                                    .hasPointsSatisfying(
                                        point ->
                                            point.hasAttributes(
                                                Attributes.of(
                                                    stringKey("pool.name"), poolName))))));
    if (testConnectionTimeouts) {
      testing.waitAndAssertMetrics(
          instrumentationName,
          "db.client.connections.timeouts",
          metrics ->
              metrics.anySatisfy(
                  metric ->
                      assertThat(metric)
                          .hasUnit("timeouts")
                          .hasDescription(
                              "The number of connection timeouts that have occurred trying to obtain a connection from the pool.")
                          .hasLongSumSatisfying(
                              sum ->
                                  sum.isMonotonic()
                                      .hasPointsSatisfying(
                                          point ->
                                              point.hasAttributes(
                                                  Attributes.of(
                                                      stringKey("pool.name"), poolName))))));
    }
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.create_time",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasUnit("ms")
                        .hasDescription("The time it took to create a new connection.")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point.hasAttributes(
                                            Attributes.of(stringKey("pool.name"), poolName))))));
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.wait_time",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasUnit("ms")
                        .hasDescription(
                            "The time it took to obtain an open connection from the pool.")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point.hasAttributes(
                                            Attributes.of(stringKey("pool.name"), poolName))))));
    testing.waitAndAssertMetrics(
        instrumentationName,
        "db.client.connections.use_time",
        metrics ->
            metrics.anySatisfy(
                metric ->
                    assertThat(metric)
                        .hasUnit("ms")
                        .hasDescription(
                            "The time between borrowing a connection and returning it to the pool.")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point.hasAttributes(
                                            Attributes.of(stringKey("pool.name"), poolName))))));
  }
}
