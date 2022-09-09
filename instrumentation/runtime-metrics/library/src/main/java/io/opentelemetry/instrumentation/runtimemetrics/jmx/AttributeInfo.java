/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.jmx;

/**
 * A class holding relevant information about an MBean atribute which will be used for collecting
 * metric values. The info comes directly from the relevant MBeans.
 */
class AttributeInfo {

  private boolean usesDoubles;
  private String description;

  AttributeInfo(Number sampleValue, String description) {
    if (sampleValue instanceof Byte
        || sampleValue instanceof Short
        || sampleValue instanceof Integer
        || sampleValue instanceof Long) {
      // will use Long values
      usesDoubles = false;
    } else {
      usesDoubles = true;
    }
    this.description = description;
  }

  boolean usesDoubleValues() {
    return usesDoubles;
  }

  String getDescription() {
    return description;
  }

  /**
   * It is unlikely, but possible, that among the MBeans matching some ObjectName pattern,
   * attributes with the same name but different types exist. In such cases we have to use a metric
   * type which will be able to handle all of these attributes.
   *
   * @param other another AttributeInfo apparently for the same MBean attribute, must not be null
   */
  void updateFrom(AttributeInfo other) {
    if (other.usesDoubleValues()) {
      usesDoubles = true;
    }
    if (description == null) {
      description = other.getDescription();
    }
  }
}
