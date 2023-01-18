/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.yaml;

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

  public String getMetric() {
    return metric;
  }

  public void setMetric(String metric) {
    this.metric = validateMetricName(metric.trim());
  }

  private String validateMetricName(String name) {
    requireNonEmpty(name, "The metric name is empty");
    return name;
  }

  public String getDesc() {
    return desc;
  }

  public void setDesc(String desc) {
    // No constraints on description
    this.desc = desc.trim();
  }

  MetricInfo buildMetricInfo(
      @Nullable String prefix,
      String attributeName,
      String defaultUnit,
      MetricInfo.Type defaultType) {
    String metricName;
    if (metric == null) {
      metricName = prefix == null ? attributeName : (prefix + attributeName);
    } else {
      metricName = prefix == null ? metric : (prefix + metric);
    }

    MetricInfo.Type metricType = getMetricType();
    if (metricType == null) {
      metricType = defaultType;
    }

    String unit = getUnit();
    if (unit == null) {
      unit = defaultUnit;
    }

    return new MetricInfo(metricName, desc, unit, metricType);
  }
}
