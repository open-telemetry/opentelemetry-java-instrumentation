/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import javax.annotation.Nullable;

/**
 * A class providing the user visible characteristics (name, type, description and units) of a
 * metric to be reported with OpenTelemetry.
 *
 * <p>Objects of this class are immutable.
 */
public class MetricInfo {

  // OpenTelemetry asynchronous instrument types that can be used
  public enum Type {
    COUNTER,
    UPDOWNCOUNTER,
    GAUGE,
    /** state metric captured as updowncounter */
    STATE
  }

  // How to report the metric using OpenTelemetry API
  private final String metricName; // used as Instrument name
  @Nullable private final String description;
  @Nullable private final String unit;
  private final Type type;

  /**
   * Constructor for MetricInfo.
   *
   * @param metricName a String that will be used as a metric name, it should be unique
   * @param description a human readable description of the metric
   * @param unit a human readable unit of measurement
   * @param type the instrument typ to be used for the metric
   */
  public MetricInfo(
      String metricName, @Nullable String description, String unit, @Nullable Type type) {
    this.metricName = metricName;
    this.description = description;
    this.unit = unit;
    this.type = type == null ? Type.GAUGE : type;
  }

  public String getMetricName() {
    return metricName;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  @Nullable
  public String getUnit() {
    return unit;
  }

  public Type getType() {
    return type;
  }
}
