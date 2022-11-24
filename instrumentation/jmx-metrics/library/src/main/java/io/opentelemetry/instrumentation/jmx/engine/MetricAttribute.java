/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import javax.management.MBeanServer;
import javax.management.ObjectName;

/**
 * A class representing a metric attribute. It is responsible for extracting the attribute value (to
 * be reported as a Measurement attribute), and for holding the corresponding attribute name to be
 * used. Objects of this class are immutable.
 */
public class MetricAttribute {
  private final String name;
  private final MetricAttributeExtractor extractor;

  public MetricAttribute(String name, MetricAttributeExtractor extractor) {
    this.name = name;
    this.extractor = extractor;
  }

  public String getAttributeName() {
    return name;
  }

  String acquireAttributeValue(MBeanServer server, ObjectName objectName) {
    return extractor.extractValue(server, objectName);
  }
}
