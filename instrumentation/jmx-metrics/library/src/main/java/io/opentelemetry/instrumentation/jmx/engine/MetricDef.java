/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

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
//  MetricBanner poolUsedBanner =
//      new MetricBanner(
//          "my.own.jvm.memory.pool.used",
//          "Pool memory currently used",
//          "By",
//          MetricBanner.Type.UPDOWNCOUNTER);
//  MetricBanner poolLimitBanner =
//      new MetricBanner(
//          "my.own.jvm.memory.pool.limit",
//          "Maximum obtainable memory pool size",
//          "By",
//          MetricBanner.Type.UPDOWNCOUNTER);
//
//  MetricExtractor usageUsedExtractor =
//      new MetricExtractor(
//          new BeanAttributeExtractor("Usage", "used"),
//          poolUsedBanner,
//          poolAttribute,
//          typeAttribute);
//  MetricExtractor usageMaxExtractor =
//      new MetricExtractor(
//          new BeanAttributeExtractor("Usage", "max"),
//          poolLimitBanner,
//          poolAttribute,
//          typeAttribute);
//
//  MetricDef def =
//      new MetricDef(
//          new BeanPack(null, new ObjectName("java.lang:name=*,type=MemoryPool")),
//          usageUsedExtractor,
//          usageMaxExtractor);

public class MetricDef {

  // Describes the MBeans to use
  private final BeanPack beans;

  // Describes how to get the metric values and their attributes, and how to report them
  private final MetricExtractor[] metricExtractors;

  /**
   * Constructor for MetricDef.
   *
   * @param beans description of MBeans required to obtain metric values
   * @param metricExtractors description of how to extract metric values; if more than one
   *     MetricExtractor is provided, they should use unique metric names or unique metric
   *     attributes
   */
  public MetricDef(BeanPack beans, MetricExtractor... metricExtractors) {
    this.beans = beans;
    this.metricExtractors = metricExtractors;
  }

  BeanPack getBeanPack() {
    return beans;
  }

  MetricExtractor[] getMetricExtractors() {
    return metricExtractors;
  }
}
