/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * A class representing a metric attribute (label). It is responsible for extracting a label value
 * (to be reported as a Measurement attribute), and for holding the corresponding label name to be
 * used. Objects of this class are immutable.
 */
public class MetricLabel {
  private final String labelName;
  private final LabelExtractor extractor;

  public MetricLabel(String labelName, LabelExtractor extractor) {
    this.labelName = labelName;
    this.extractor = extractor;
  }

  public String getLabelName() {
    return labelName;
  }

  String extractLabelValue(MBeanServer server, ObjectName objectName) {
    return extractor.extractValue(server, objectName);
  }

  public static LabelExtractor fromConstant(String constantValue) {
    return (a, b) -> {
      return constantValue;
    };
  }

  public static LabelExtractor fromParameter(String parameterKey) {
    if (parameterKey.isEmpty()) {
      throw new IllegalArgumentException("Empty parameter name");
    }
    return (dummy, objectName) -> {
      return objectName.getKeyProperty(parameterKey);
    };
  }

  public static LabelExtractor fromAttribute(String attributeName) {
    return AttributeValueExtractor.fromName(attributeName);
  }
}
