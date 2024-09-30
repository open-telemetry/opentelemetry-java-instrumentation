/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.yaml;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.jmx.engine.MetricAttribute;
import io.opentelemetry.instrumentation.jmx.engine.MetricAttributeExtractor;
import io.opentelemetry.instrumentation.jmx.engine.MetricInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An abstract class containing skeletal info about Metrics:
 * <li>the metric type
 * <li>the metric attributes
 * <li>the unit
 *
 *     <p>Known subclasses are {@link JmxRule} and {@link Metric}.
 */
abstract class MetricStructure {

  // Used by the YAML parser
  //    type: TYPE
  //    metricAttribute:
  //      KEY1: SPECIFICATION1
  //      KEY2: SPECIFICATION2
  //    unit: UNIT
  //    stateMapping:
  //      state1: [a,b]
  //      state2: c
  //      state3: '*'

  private Map<String, String> metricAttribute;
  private StateMapping stateMapping = StateMapping.empty();
  private static final String STATE_MAPPING_WILDCARD = "*";
  private String unit;

  private MetricInfo.Type metricType;
  private List<MetricAttribute> metricAttributes;

  MetricStructure() {}

  void setType(String t) {
    // Do not complain about case variations
    t = t.trim().toUpperCase(Locale.ROOT);
    this.metricType = MetricInfo.Type.valueOf(t);
  }

  public String getUnit() {
    return unit;
  }

  public void setUnit(String unit) {
    this.unit = validateUnit(unit.trim());
  }

  void setStateMapping(Map<String, Object> stateMapping) {
    StateMapping.Builder builder = StateMapping.builder();
    for (Map.Entry<String, Object> entry : stateMapping.entrySet()) {
      String stateKey = entry.getKey();
      Object stateValue = entry.getValue();
      if (stateValue instanceof String) {
        addMappedValue(builder, (String) stateValue, stateKey);
      } else if (stateValue instanceof List) {
        for (Object listEntry : (List<?>) stateValue) {
          if (!(listEntry instanceof String)) {
            throw new IllegalArgumentException("unexpected state list value: " + stateKey);
          }
          addMappedValue(builder, (String) listEntry, stateKey);
        }
      } else {
        throw new IllegalArgumentException("unexpected state value: " + stateValue);
      }
    }
    this.stateMapping = builder.build();
  }

  private static void addMappedValue(
      StateMapping.Builder builder, String stateValue, String stateKey) {
    if (stateValue.equals(STATE_MAPPING_WILDCARD)) {
      builder.withDefaultState(stateKey);
    } else {
      builder.withMappedValue(stateValue, stateKey);
    }
  }

  @CanIgnoreReturnValue
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
    this.metricAttributes = addMetricAttributes(map);
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

  private List<MetricAttribute> addMetricAttributes(Map<String, String> metricAttributeMap) {

    List<MetricAttribute> list = new ArrayList<>();
    boolean hasStateAttribute = false;
    for (String key : metricAttributeMap.keySet()) {
      String target = metricAttributeMap.get(key);
      if (target == null) {
        throw new IllegalStateException("nothing specified for metric attribute key '" + key + "'");
      }
      MetricAttribute attribute = buildMetricAttribute(key, target.trim());
      if (attribute.isStateAttribute()) {
        if (hasStateAttribute) {
          throw new IllegalArgumentException("only one state attribute is allowed");
        }
        hasStateAttribute = true;
      }
      list.add(attribute);
    }
    if (hasStateAttribute && stateMapping.isEmpty()) {
      throw new IllegalArgumentException("statekey() usage without stateMapping");
    }
    return list;
  }

  private static MetricAttribute buildMetricAttribute(String key, String target) {
    // The recognized forms of target are:
    //  - param(STRING)
    //  - beanattr(STRING)
    //  - const(STRING)
    //  - statekey()
    // where STRING is the name of the corresponding parameter key, attribute name,
    // or the direct value to use
    int k = target.indexOf(')');

    // Check for one of the cases as above
    if (target.startsWith("param(")) {
      if (k > 0) {
        String jmxAttribute = target.substring(6, k).trim();
        return new MetricAttribute(
            key, MetricAttributeExtractor.fromObjectNameParameter(jmxAttribute));
      }
    } else if (target.startsWith("beanattr(")) {
      if (k > 0) {
        String jmxAttribute = target.substring(9, k).trim();
        return new MetricAttribute(key, MetricAttributeExtractor.fromBeanAttribute(jmxAttribute));
      }
    } else if (target.startsWith("const(")) {
      if (k > 0) {
        String constantValue = target.substring(6, k).trim();
        return new MetricAttribute(key, MetricAttributeExtractor.fromConstant(constantValue));
      }
    } else if (target.equals("statekey()")) {
      return new MetricAttribute(key, null);
    }

    String msg = "Invalid metric attribute specification for '" + key + "': " + target;
    throw new IllegalArgumentException(msg);
  }

  public StateMapping getStateMapping() {
    return stateMapping;
  }
}
