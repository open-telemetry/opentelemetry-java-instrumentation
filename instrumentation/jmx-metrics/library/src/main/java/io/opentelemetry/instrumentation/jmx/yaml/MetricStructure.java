/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.yaml;

import io.opentelemetry.instrumentation.jmx.engine.MetricAttribute;
import io.opentelemetry.instrumentation.jmx.engine.MetricAttributeExtractor;
import io.opentelemetry.instrumentation.jmx.engine.MetricInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An abstract class containing skeletal info about Metrics:
 * <li>the metric type
 * <li>the metric attributes
 * <li>the unit
 *
 *     <p>Known subclasses are JmxRule and Metric.
 */
abstract class MetricStructure {

  // Used by the YAML parser
  //    type: TYPE
  //    metricAttribute:
  //      KEY1: SPECIFICATION1
  //      KEY2: SPECIFICATION2
  //    unit: UNIT

  private String type; // unused, for YAML parser only
  private Map<String, String> metricAttribute; // unused, for YAML parser only
  private String unit;

  private MetricInfo.Type metricType;
  private List<MetricAttribute> metricAttributes;

  public String getType() {
    return type;
  }

  public void setType(String t) {
    // Do not complain about case variations
    t = t.trim().toUpperCase();
    this.metricType = MetricInfo.Type.valueOf(t);
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
   * When the YAML parser sets the metric attributes (as Strings), convert them immediately to
   * MetricAttribute objects. Any errors during conversion will show in the context of the parsed
   * YAML file.
   *
   * @param map the mapping of metric attribute keys to evaluating snippets
   */
  public void setMetricAttribute(Map<String, String> map) {
    this.metricAttribute = map;
    // pre-build the MetricAttributes
    List<MetricAttribute> attrList = new ArrayList<>();
    addMetricAttributes(attrList, map);
    this.metricAttributes = attrList;
  }

  // Used only for testing
  public Map<String, String> getMetricAttribute() {
    return metricAttribute;
  }

  public MetricInfo.Type getMetricType() {
    return metricType;
  }

  protected List<MetricAttribute> getAttributeList() {
    return metricAttributes;
  }

  protected void requireNonEmpty(String s, String msg) {
    if (s.isEmpty()) {
      throw new IllegalArgumentException(msg);
    }
  }

  private static void addMetricAttributes(
      List<MetricAttribute> list, Map<String, String> metricAttributeMap) {
    if (metricAttributeMap != null) {
      for (String key : metricAttributeMap.keySet()) {
        String target = metricAttributeMap.get(key);
        if (target == null) {
          throw new IllegalStateException(
              "nothing specified for metric attribute key '" + key + "'");
        }
        list.add(buildMetricAttribute(key, target.trim()));
      }
    }
  }

  private static MetricAttribute buildMetricAttribute(String key, String target) {
    // The recognized forms of target are:
    //  - param(STRING)
    //  - beanattr(STRING)
    //  - const(STRING)
    // where STRING is the name of the corresponding parameter key, attribute name,
    // or the direct value to use
    int k = target.indexOf(')');

    // Check for one of the cases as above
    if (target.startsWith("param(")) {
      if (k > 0) {
        return new MetricAttribute(
            key, MetricAttributeExtractor.fromObjectNameParameter(target.substring(6, k).trim()));
      }
    } else if (target.startsWith("beanattr(")) {
      if (k > 0) {
        return new MetricAttribute(
            key, MetricAttributeExtractor.fromBeanAttribute(target.substring(9, k).trim()));
      }
    } else if (target.startsWith("const(")) {
      if (k > 0) {
        return new MetricAttribute(
            key, MetricAttributeExtractor.fromConstant(target.substring(6, k).trim()));
      }
    }

    throw new IllegalArgumentException("Invalid metric attribute specification for '" + key + "'");
  }
}
