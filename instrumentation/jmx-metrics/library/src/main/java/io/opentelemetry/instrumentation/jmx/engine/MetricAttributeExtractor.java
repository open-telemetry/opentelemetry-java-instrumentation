/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.engine;

import javax.annotation.Nullable;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

/**
 * MetricAttributeExtractors are responsible for obtaining values for populating metric attributes,
 * i.e. measurement attributes.
 */
public interface MetricAttributeExtractor {

  /**
   * Provide a String value to be used as the value of a metric attribute.
   *
   * @param connection MBeanServer to query, must not be null if the extraction is from an MBean
   *     attribute
   * @param objectName the identifier of the MBean to query, must not be null if the extraction is
   *     from an MBean attribute, or from the ObjectName parameter
   * @return the value of the attribute, can be null if extraction failed
   */
  @Nullable
  String extractValue(@Nullable MBeanServerConnection connection, @Nullable ObjectName objectName);

  static MetricAttributeExtractor fromConstant(String constantValue) {
    return (a, b) -> constantValue;
  }

  static MetricAttributeExtractor fromObjectNameParameter(String parameterKey) {
    if (parameterKey.isEmpty()) {
      throw new IllegalArgumentException("Empty parameter name");
    }
    return (dummy, objectName) -> objectName.getKeyProperty(parameterKey);
  }

  static MetricAttributeExtractor fromBeanAttribute(String attributeName) {
    return BeanAttributeExtractor.fromName(attributeName);
  }
}
