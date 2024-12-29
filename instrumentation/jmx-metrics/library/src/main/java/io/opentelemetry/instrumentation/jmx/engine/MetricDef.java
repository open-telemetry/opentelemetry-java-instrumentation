/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import java.util.List;

/**
 * A class providing a complete definition on how to create an Open Telemetry metric out of the JMX
 * system: how to extract values from MBeans and how to model, name and decorate them with
 * attributes using OpenTelemetry Metric API. Objects of this class are immutable.
 */

// Example: The rule described by the following YAML definition
//
//  - bean: java.lang:name=*,type=MemoryPool
//    metricAttribute:
//      pool: param(name)
//      type: beanattr(Type)
//    mapping:
//      Usage.used:
//        metric: my.own.jvm.memory.pool.used
//        type: updowncounter
//        desc: Pool memory currently used
//        unit: By
//      Usage.max:
//        metric: my.own.jvm.memory.pool.max
//        type: updowncounter
//        desc: Maximum obtainable memory pool size
//        unit: By
//
// can be created using the following snippet:
//
//  MetricAttribute poolAttribute =
//      new MetricAttribute("pool", MetricAttributeExtractor.fromObjectNameParameter("name"));
//  MetricAttribute typeAttribute =
//      new MetricAttribute("type", MetricAttributeExtractor.fromBeanAttribute("Type"));
//
//  MetricInfo poolUsedInfo =
//      new MetricInfo(
//          "my.own.jvm.memory.pool.used",
//          "Pool memory currently used",
//          "By",
//          MetricInfo.Type.UPDOWNCOUNTER);
//  MetricInfo poolLimitInfo =
//      new MetricInfo(
//          "my.own.jvm.memory.pool.limit",
//          "Maximum obtainable memory pool size",
//          "By",
//          MetricInfo.Type.UPDOWNCOUNTER);
//
//  MetricExtractor usageUsedExtractor =
//      new MetricExtractor(
//          new BeanAttributeExtractor("Usage", "used"),
//          poolUsedInfo,
//          poolAttribute,
//          typeAttribute);
//  MetricExtractor usageMaxExtractor =
//      new MetricExtractor(
//          new BeanAttributeExtractor("Usage", "max"),
//          poolLimitInfo,
//          poolAttribute,
//          typeAttribute);
//
//  MetricDef def =
//      new MetricDef(
//          new BeanGroup(null, new ObjectName("java.lang:name=*,type=MemoryPool")),
//          usageUsedExtractor,
//          usageMaxExtractor);

public class MetricDef {

  // Describes the MBeans to use
  private final BeanGroup beans;

  // Describes how to get the metric values and their attributes, and how to report them
  private final List<MetricExtractor> metricExtractors;

  /**
   * Constructor for MetricDef.
   *
   * @param beans description of MBeans required to obtain metric values
   * @param metricExtractors description of how to extract metric values; if more than one
   *     MetricExtractor is provided, they should use unique metric names or unique metric
   *     attributes
   */
  public MetricDef(BeanGroup beans, List<MetricExtractor> metricExtractors) {
    this.beans = beans;
    this.metricExtractors = metricExtractors;
  }

  BeanGroup getBeanGroup() {
    return beans;
  }

  List<MetricExtractor> getMetricExtractors() {
    return metricExtractors;
  }
}
