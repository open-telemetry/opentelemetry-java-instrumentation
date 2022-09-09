/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.jmx;

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
  private final AttributeValueExtractor attributeExtractor;

  // Defines the Measurement attributes to be used when reporting the metric value.
  // We call them labels to avoid confusion with MBean attributes
  private final MetricLabel[] labels;

  private volatile DetectionStatus status;

  public MetricExtractor(
      AttributeValueExtractor attributeExtractor, MetricBanner banner, MetricLabel... labels) {
    this.attributeExtractor = attributeExtractor;
    this.banner = banner;
    this.labels = labels;
  }

  MetricBanner getBanner() {
    return banner;
  }

  AttributeValueExtractor getMetricValueExtractor() {
    return attributeExtractor;
  }

  MetricLabel[] getLabels() {
    return labels;
  }

  void setStatus(DetectionStatus status) {
    this.status = status;
  }

  DetectionStatus getStatus() {
    return status;
  }
}
