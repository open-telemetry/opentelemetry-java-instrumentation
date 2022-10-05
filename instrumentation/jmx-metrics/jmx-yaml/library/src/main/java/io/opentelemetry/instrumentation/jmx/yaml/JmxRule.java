/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.yaml;

import io.opentelemetry.instrumentation.jmx.engine.AttributeValueExtractor;
import io.opentelemetry.instrumentation.jmx.engine.BeanPack;
import io.opentelemetry.instrumentation.jmx.engine.MetricBanner;
import io.opentelemetry.instrumentation.jmx.engine.MetricDef;
import io.opentelemetry.instrumentation.jmx.engine.MetricExtractor;
import io.opentelemetry.instrumentation.jmx.engine.MetricLabel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
  private String bean;
  private List<String> beans;
  private String prefix;
  private Map<String, Metric> mapping;

  public String getBean() {
    return bean;
  }

  public void setBean(String bean) throws Exception {
    this.bean = validateBean(bean);
  }

  public List<String> getBeans() {
    return beans;
  }

  private static String validateBean(String name) throws MalformedObjectNameException {
    String trimmed = name.trim();
    // Check the syntax of the provided name by attempting to create an ObjectName from it.
    new ObjectName(trimmed);
    return trimmed;
  }

  public void setBeans(List<String> beans) throws Exception {
    List<String> list = new ArrayList<>();
    for (String name : beans) {
      list.add(validateBean(name));
    }
    this.beans = list;
  }

  public void setPrefix(String prefix) {
    this.prefix = validatePrefix(prefix.trim());
  }

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

  private static Map<String, Metric> validateAttributeMapping(Map<String, Metric> mapping) {
    if (mapping.isEmpty()) {
      throw new IllegalStateException("No MBean attributes specified");
    }

    // Make sure that all attribute names are well-formed by creating the corresponding
    // AttributeValueExtractors
    Set<String> attrNames = mapping.keySet();
    for (String attributeName : attrNames) {
      // check if AttributeValueExtractors can be built without exceptions
      AttributeValueExtractor.fromName(attributeName);
    }
    return mapping;
  }

  /**
   * Convert this rule to a complete MetricDefinition object. If the rule is incomplete or has
   * consistency or semantic issues, an exception will be thrown.
   *
   * @return a valid MetricDefinition object
   * @throws an exception if any issues within the rule are detected
   */
  public MetricDef buildMetricDef() throws Exception {
    BeanPack pack;
    if (bean != null) {
      pack = new BeanPack(null, new ObjectName(bean));
    } else if (beans != null && !beans.isEmpty()) {
      ObjectName[] objectNames = new ObjectName[beans.size()];
      int k = 0;
      for (String oneBean : beans) {
        objectNames[k++] = new ObjectName(oneBean);
      }
      pack = new BeanPack(null, objectNames);
    } else {
      throw new IllegalStateException("No ObjectName specified");
    }

    if (mapping == null || mapping.isEmpty()) {
      throw new IllegalStateException("No MBean attributes specified");
    }

    Set<String> attrNames = mapping.keySet();
    MetricExtractor[] metricExtractors = new MetricExtractor[attrNames.size()];
    int n = 0;
    for (String attributeName : attrNames) {
      MetricBanner banner;
      Metric m = mapping.get(attributeName);
      if (m == null) {
        banner =
            new MetricBanner(
                prefix == null ? attributeName : (prefix + attributeName),
                null,
                getUnit(),
                getMetricType());
      } else {
        banner = m.buildMetricBanner(prefix, attributeName, getUnit(), getMetricType());
      }
      AttributeValueExtractor attrExtractor = AttributeValueExtractor.fromName(attributeName);

      List<MetricLabel> labelList;
      List<MetricLabel> ownLabels = getLabels();
      if (ownLabels != null && m != null && m.getLabels() != null) {
        // MetricLabels have been specified at two levels, need to combine them
        labelList = combineLabels(ownLabels, m.getLabels());
      } else if (ownLabels != null) {
        labelList = ownLabels;
      } else if (m != null && m.getLabels() != null) {
        // Get the labels from the metric
        labelList = m.getLabels();
      } else {
        // There are no labels at all
        labelList = new ArrayList<MetricLabel>();
      }

      MetricExtractor metricExtractor =
          new MetricExtractor(
              attrExtractor, banner, labelList.toArray(new MetricLabel[labelList.size()]));
      metricExtractors[n++] = metricExtractor;
    }

    return new MetricDef(pack, metricExtractors);
  }

  private static List<MetricLabel> combineLabels(
      List<MetricLabel> ownLabels, List<MetricLabel> metricLabels) {
    Map<String, MetricLabel> set = new HashMap<>();
    for (MetricLabel ownLabel : ownLabels) {
      set.put(ownLabel.getLabelName(), ownLabel);
    }

    // Let the metric level defined labels override own lables
    for (MetricLabel metricLabel : metricLabels) {
      set.put(metricLabel.getLabelName(), metricLabel);
    }

    List<MetricLabel> result = new ArrayList<MetricLabel>();
    result.addAll(set.values());
    return result;
  }
}
