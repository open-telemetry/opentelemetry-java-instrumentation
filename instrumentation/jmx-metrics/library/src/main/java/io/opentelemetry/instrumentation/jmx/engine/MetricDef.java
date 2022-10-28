/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

/**
 * A class providing a complete definition on how to create an Open Telemetry metric out of the JMX
 * system: how to extract values from MBeans and how to model, name and decorate them with
 * attributes using OpenTelemetry Metric API. Objects of this class are immutable.
 *
 * <p>Example: The JVM provides an MBean with ObjectName "java.lang:type=Threading", and one of the
 * MBean attributes is "ThreadCount". This MBean can be used to provide a metric showing the current
 * number of threads run by the JVM as follows:
 *
 * <p>new MetricDef(new BeanPack(null, new ObjectName("java.lang:type=Threading")),
 *
 * <p>new MetricExtractor(new BeanAttributeExtractor("ThreadCount"),
 *
 * <p>new MetricBanner("process.runtime.jvm.threads", "Current number of threads", "1" )));
 */
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
