/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import java.util.List;
import javax.annotation.Nullable;

/**
 * A class holding the info needed to support a single metric: how to define it in OpenTelemetry and
 * how to provide the metric values.
 *
 * <p>Objects of this class are stateful, the DetectionStatus may change over time to keep track of
 * all ObjectNames that should be used to deliver the metric values.
 */
public class MetricExtractor {

  private final MetricInfo metricInfo;

  // Defines the way to access the metric value (a number)
  private final BeanAttributeExtractor attributeExtractor;

  // Defines the Measurement attributes to be used when reporting the metric value.
  private final List<MetricAttribute> attributes;

  @Nullable private volatile DetectionStatus status;

  public MetricExtractor(
      BeanAttributeExtractor attributeExtractor,
      MetricInfo metricInfo,
      List<MetricAttribute> attributes) {
    this.attributeExtractor = attributeExtractor;
    this.metricInfo = metricInfo;
    this.attributes = attributes;
  }

  MetricInfo getInfo() {
    return metricInfo;
  }

  BeanAttributeExtractor getMetricValueExtractor() {
    return attributeExtractor;
  }

  List<MetricAttribute> getAttributes() {
    return attributes;
  }

  void setStatus(DetectionStatus status) {
    this.status = status;
  }

  @Nullable
  DetectionStatus getStatus() {
    return status;
  }
}
