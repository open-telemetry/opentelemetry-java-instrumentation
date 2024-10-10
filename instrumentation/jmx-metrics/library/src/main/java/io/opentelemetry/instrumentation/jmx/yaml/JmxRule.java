/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.yaml;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.jmx.engine.BeanAttributeExtractor;
import io.opentelemetry.instrumentation.jmx.engine.BeanGroup;
import io.opentelemetry.instrumentation.jmx.engine.MetricAttribute;
import io.opentelemetry.instrumentation.jmx.engine.MetricAttributeExtractor;
import io.opentelemetry.instrumentation.jmx.engine.MetricDef;
import io.opentelemetry.instrumentation.jmx.engine.MetricExtractor;
import io.opentelemetry.instrumentation.jmx.engine.MetricInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

/**
 * This class represents a complete JMX metrics rule as defined by a YAML file. Objects of this
 * class are created and populated by the YAML parser.
 */
public class JmxRule extends MetricStructure {

  // Used by the YAML parser
  //   bean: OBJECT_NAME
  //   beans:
  //     - OBJECTNAME1
  //     - OBJECTNAME2
  //   prefix: METRIC_NAME_PREFIX
  //   mapping:
  //     ATTRIBUTE1:
  //       METRIC_FIELDS1
  //     ATTRIBUTE2:
  //     ATTRIBUTE3:
  //       METRIC_FIELDS3
  // The parser never calls setters for these fields with null arguments
  @Nullable private String bean;
  private List<String> beans;
  @Nullable private String prefix;
  private Map<String, Metric> mapping;

  public String getBean() {
    return bean;
  }

  public void setBean(String bean) {
    this.bean = validateBean(bean);
  }

  public List<String> getBeans() {
    return beans;
  }

  private static String validateBean(String name) {
    try {
      String trimmed = name.trim();
      // Check the syntax of the provided name by attempting to create an ObjectName from it.
      new ObjectName(trimmed);
      return trimmed;
    } catch (MalformedObjectNameException e) {
      throw new IllegalArgumentException("'" + name + "' is not a valid JMX object name", e);
    }
  }

  public void setBeans(List<String> beans) {
    List<String> list = new ArrayList<>();
    for (String name : beans) {
      list.add(validateBean(name));
    }
    this.beans = list;
  }

  public void setPrefix(String prefix) {
    this.prefix = validatePrefix(prefix.trim());
  }

  @CanIgnoreReturnValue
  private String validatePrefix(String prefix) {
    // Do not accept empty string.
    // While it is theoretically acceptable, it probably indicates a user error.
    requireNonEmpty(prefix, "The metric name prefix is empty");
    return prefix;
  }

  public String getPrefix() {
    return prefix;
  }

  public Map<String, Metric> getMapping() {
    return mapping;
  }

  public void setMapping(Map<String, Metric> mapping) {
    this.mapping = validateAttributeMapping(mapping);
  }

  @CanIgnoreReturnValue
  private static Map<String, Metric> validateAttributeMapping(Map<String, Metric> mapping) {
    if (mapping.isEmpty()) {
      throw new IllegalStateException("No MBean attributes specified");
    }

    // Make sure that all attribute names are well-formed by creating the corresponding
    // BeanAttributeExtractors
    Set<String> attrNames = mapping.keySet();
    for (String attributeName : attrNames) {
      // check if BeanAttributeExtractors can be built without exceptions
      BeanAttributeExtractor.fromName(attributeName);
    }
    return mapping;
  }

  /**
   * Convert this rule to a complete MetricDefinition object. If the rule is incomplete or has
   * consistency or semantic issues, an exception will be thrown.
   *
   * @return a valid MetricDefinition object
   * @throws Exception an exception if any issues within the rule are detected
   */
  public MetricDef buildMetricDef() throws Exception {
    BeanGroup group;
    if (bean != null) {
      group = BeanGroup.forSingleBean(bean);
    } else if (beans != null && !beans.isEmpty()) {
      group = BeanGroup.forBeans(beans);
    } else {
      throw new IllegalStateException("No ObjectName specified");
    }

    if (mapping == null || mapping.isEmpty()) {
      throw new IllegalStateException("No MBean attributes specified");
    }

    Set<String> attrNames = mapping.keySet();
    List<MetricExtractor> metricExtractors = new ArrayList<>();
    for (String attributeName : attrNames) {
      BeanAttributeExtractor attrExtractor = BeanAttributeExtractor.fromName(attributeName);
      // This is essentially the same as 'attributeName' but with escape characters removed
      String niceAttributeName = attrExtractor.getAttributeName();
      MetricInfo metricInfo;
      Metric m = mapping.get(attributeName);
      if (m == null) {
        metricInfo =
            new MetricInfo(
                prefix == null ? niceAttributeName : (prefix + niceAttributeName),
                null,
                getUnit(),
                getMetricType());
      } else {
        metricInfo = m.buildMetricInfo(prefix, niceAttributeName, getUnit(), getMetricType());
      }

      List<MetricAttribute> ownAttributes = getAttributeList();
      List<MetricAttribute> metricAttributes = m != null ? m.getAttributeList() : null;
      List<MetricAttribute> attributeList =
          combineMetricAttributes(ownAttributes, metricAttributes);

      // higher priority to metric level mapping, then jmx rule as fallback
      StateMapping stateMapping = getEffectiveStateMapping(m, this);

      if (stateMapping.isEmpty()) {
        metricExtractors.add(new MetricExtractor(attrExtractor, metricInfo, attributeList));
      } else {

        // generate one metric extractor per state metric key
        // each metric extractor will have the state attribute replaced with a constant
        metricExtractors.addAll(
            createStateMappingExtractors(stateMapping, attributeList, attrExtractor, metricInfo));
      }
    }

    return new MetricDef(group, metricExtractors);
  }

  private static List<MetricExtractor> createStateMappingExtractors(
      StateMapping stateMapping,
      List<MetricAttribute> attributeList,
      BeanAttributeExtractor attrExtractor,
      MetricInfo metricInfo) {

    List<MetricExtractor> extractors = new ArrayList<>();
    for (String key : stateMapping.getStateKeys()) {
      List<MetricAttribute> stateMetricAttributes = new ArrayList<>();

      for (MetricAttribute ma : attributeList) {
        // replace state metric attribute with constant key value
        if (!ma.isStateAttribute()) {
          stateMetricAttributes.add(ma);
        } else {
          stateMetricAttributes.add(
              new MetricAttribute(
                  ma.getAttributeName(), MetricAttributeExtractor.fromConstant(key)));
        }
      }

      BeanAttributeExtractor stateMetricExtractor =
          new BeanAttributeExtractor(attrExtractor.getAttributeName()) {

            @Override
            protected Object getSampleValue(
                MBeanServerConnection connection, ObjectName objectName) {
              // metric actual type is sampled in the discovery process, so we have to
              // make this extractor as extracting integers.
              return 0;
            }

            @Override
            protected Number extractNumericalAttribute(
                MBeanServerConnection connection, ObjectName objectName) {
              String rawStateValue = attrExtractor.extractValue(connection, objectName);
              String mappedStateValue = stateMapping.getStateValue(rawStateValue);
              return key.equals(mappedStateValue) ? 1 : 0;
            }
          };

      // state metric are always up/down counters
      MetricInfo stateMetricInfo =
          new MetricInfo(
              metricInfo.getMetricName(),
              metricInfo.getDescription(),
              metricInfo.getUnit(),
              MetricInfo.Type.UPDOWNCOUNTER);

      extractors.add(
          new MetricExtractor(stateMetricExtractor, stateMetricInfo, stateMetricAttributes));
    }
    return extractors;
  }

  private static List<MetricAttribute> combineMetricAttributes(
      List<MetricAttribute> ownAttributes, List<MetricAttribute> metricAttributes) {

    Map<String, MetricAttribute> set = new HashMap<>();
    if (ownAttributes != null) {
      for (MetricAttribute ownAttribute : ownAttributes) {
        set.put(ownAttribute.getAttributeName(), ownAttribute);
      }
    }
    if (metricAttributes != null) {
      // Let the metric level defined attributes override own attributes
      for (MetricAttribute metricAttribute : metricAttributes) {
        set.put(metricAttribute.getAttributeName(), metricAttribute);
      }
    }

    return new ArrayList<>(set.values());
  }

  private static StateMapping getEffectiveStateMapping(Metric m, JmxRule rule) {
    if (m == null || m.getStateMapping().isEmpty()) {
      return rule.getStateMapping();
    } else {
      return m.getStateMapping();
    }
  }
}
