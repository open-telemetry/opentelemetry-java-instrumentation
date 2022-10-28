/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

/**
 * A class holding the info needed to support a single metric: how to define it in OpenTelemetry and
 * how to provide the metric values.
 *
 * <p>Objects of this class are stateful, the DetectionStatus may change over time to keep track of
 * all ObjectNames that should be used to deliver the metric values.
 */
public class MetricExtractor {

  private final MetricBanner banner;

  // Defines the way to access the metric value (a number)
  private final BeanAttributeExtractor attributeExtractor;

  // Defines the Measurement attributes to be used when reporting the metric value.
  private final MetricAttribute[] attributes;

  private volatile DetectionStatus status;

  public MetricExtractor(
      BeanAttributeExtractor attributeExtractor,
      MetricBanner banner,
      MetricAttribute... attributes) {
    this.attributeExtractor = attributeExtractor;
    this.banner = banner;
    this.attributes = attributes;
  }

  MetricBanner getBanner() {
    return banner;
  }

  BeanAttributeExtractor getMetricValueExtractor() {
    return attributeExtractor;
  }

  MetricAttribute[] getAttributes() {
    return attributes;
  }

  void setStatus(DetectionStatus status) {
    this.status = status;
  }

  DetectionStatus getStatus() {
    return status;
  }
}
