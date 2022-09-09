/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.jmx.conf.data;

import io.opentelemetry.instrumentation.runtimemetrics.jmx.MetricBanner;
import io.opentelemetry.instrumentation.runtimemetrics.jmx.MetricLabel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An abstract class containing skeletal info about Metrics:
 * <li>the metric type
 * <li>the metric attributes (labels)
 * <li>the unit
 *
 *     <p>Known subclasses are JMXRule and Metric.
 */
abstract class MetricStructure {

  // Used by the YAML parser
  //    type: TYPE
  //    label:
  //      KEY1: SPECIFICATION1
  //      KEY2: SPECIFICATION2
  //    unit: UNIT

  private String type; // unused, for YAML parser only
  private Map<String, String> label; // unused, for YAML parser only
  private String unit;

  private MetricBanner.Type metricType;
  private List<MetricLabel> labels;

  public String getType() {
    return type;
  }

  public void setType(String t) {
    // Do not complain about case variations
    t = t.trim().toUpperCase();
    this.metricType = MetricBanner.Type.valueOf(t);
    this.type = t;
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = validateUnit(unit.trim());
  }

  private String validateUnit(String unit) {
    requireNonEmpty(unit, "Metric unit is empty");
    return unit;
  }

  /**
   * When the YAML parser sets the labels, convert them immediately to MetricLabels. Any errors
   * during conversion will show in the context of the parsed YAML file.
   *
   * @param label the mapping of metric attribute keys to evaluating snippets
   */
  public void setLabel(Map<String, String> label) {
    this.label = label;
    // pre-build the Labels
    List<MetricLabel> labelList = new ArrayList<>();
    addLabels(labelList, label);
    this.labels = labelList;
  }

  // Used only for testing
  public Map<String, String> getLabel() {
    return label;
  }

  public MetricBanner.Type getMetricType() {
    return metricType;
  }

  protected List<MetricLabel> getLabels() {
    return labels;
  }

  protected void requireNonEmpty(String s, String msg) {
    if (s.isEmpty()) {
      throw new IllegalArgumentException(msg);
    }
  }

  private static void addLabels(List<MetricLabel> list, Map<String, String> tagMap) {
    if (tagMap != null) {
      for (String key : tagMap.keySet()) {
        String target = tagMap.get(key);
        if (target == null) {
          throw new IllegalStateException("nothing specified for label key '" + key + "'");
        }
        list.add(buildLabel(key, target.trim()));
      }
    }
  }

  private static MetricLabel buildLabel(String key, String target) {
    // The recognized forms of target are:
    //  - param(STRING)
    //  - attrib(STRING)
    //  - STRING
    // where STRING is the name of the corresponding parameter key, attribute name,
    // or the direct value to use
    int k = target.indexOf(')');

    // Check for one of the cases as above
    if (target.startsWith("param(")) {
      if (k > 0) {
        return new MetricLabel(key, MetricLabel.fromParameter(target.substring(6, k).trim()));
      }
    } else if (target.startsWith("attrib(")) {
      if (k > 0) {
        return new MetricLabel(key, MetricLabel.fromAttribute(target.substring(7, k).trim()));
      }
    } else if (k < 0) {
      return new MetricLabel(key, MetricLabel.fromConstant(target));
    }
    throw new IllegalArgumentException("Invalid label specification for '" + key + "'");
  }
}
