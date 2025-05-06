/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.yaml;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.jmx.engine.MetricInfo;
import javax.annotation.Nullable;

/**
 * A class representing metric definition as a part of YAML metric rule. Objects of this class are
 * created and populated by the YAML parser.
 */
public class Metric extends MetricStructure {

  // Used by the YAML parser
  //   metric: METRIC_NAME
  //   desc: DESCRIPTION
  @Nullable private String metric;
  @Nullable private String desc;

  public Metric() {}

  @Nullable
  public String getMetric() {
    return metric;
  }

  public void setMetric(String metric) {
    this.metric = validateMetricName(metric.trim());
  }

  @CanIgnoreReturnValue
  private String validateMetricName(String name) {
    requireNonEmpty(name, "The metric name is empty");
    return name;
  }

  @Nullable
  public String getDesc() {
    return desc;
  }

  public void setDesc(String desc) {
    // No constraints on description
    this.desc = desc.trim();
  }

  /**
   * @param attributeName attribute name
   * @param jmxRule parent JMX rule where metric is defined
   * @return metric info
   * @throws IllegalStateException when effective metric definition is invalid
   */
  MetricInfo buildMetricInfo(String attributeName, JmxRule jmxRule) {
    String metricName;

    String prefix = jmxRule.getPrefix();
    if (metric == null) {
      metricName = prefix == null ? attributeName : (prefix + attributeName);
    } else {
      metricName = prefix == null ? metric : (prefix + metric);
    }

    MetricInfo.Type metricType = getMetricType();
    if (metricType == null) {
      metricType = jmxRule.getMetricType();
    }
    if (metricType == null) {
      metricType = MetricInfo.Type.GAUGE;
    }

    String sourceUnit = getSourceUnit();
    if (sourceUnit == null) {
      sourceUnit = jmxRule.getSourceUnit();
    }

    String unit;
    if (!getStateMapping().isEmpty()) {
      // state metrics do not have a unit, use empty string
      unit = "";
    } else {
      unit = getUnit();
      if (unit == null) {
        unit = jmxRule.getUnit();
      }
      if (unit == null) {
        throw new IllegalStateException(
            String.format("Metric unit is required for metric '%s'", metricName));
      }
    }

    return new MetricInfo(metricName, desc, sourceUnit, unit, metricType);
  }
}
