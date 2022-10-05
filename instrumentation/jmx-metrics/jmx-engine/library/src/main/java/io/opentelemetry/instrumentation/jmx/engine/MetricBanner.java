/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

/**
 * A class providing the user visible characteristics (name, type, description and units) of a
 * metric to be reported with OpenTelemetry.
 *
 * <p>Objects of this class are immutable.
 */
public class MetricBanner {

  // OpenTelemetry asynchronous instrument types that can be used
  public enum Type {
    COUNTER,
    UPDOWNCOUNTER,
    GAUGE
  }

  // How to report the metric using OpenTelemetry API
  private final String metricName; // used as Instrument name
  private final String description;
  private final String unit;
  private final Type type;

  /**
   * Constructor for MetricBanner.
   *
   * @param metricName a String that will be used as a metric name, it should be unique
   * @param description a human readable description of the metric
   * @param unit a human readable unit of measurement
   * @param type the instrument typ to be used for the metric
   */
  public MetricBanner(String metricName, String description, String unit, Type type) {
    this.metricName = metricName;
    this.description = description;
    this.unit = unit;
    this.type = type == null ? Type.GAUGE : type;
  }

  String getMetricName() {
    return metricName;
  }

  String getDescription() {
    return description;
  }

  String getUnit() {
    return unit;
  }

  Type getType() {
    return type;
  }
}
