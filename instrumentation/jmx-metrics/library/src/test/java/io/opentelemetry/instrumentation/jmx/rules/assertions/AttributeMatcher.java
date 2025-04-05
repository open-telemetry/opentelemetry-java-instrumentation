/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.assertions;

import javax.annotation.Nullable;

/** Implements functionality of matching data point attributes. */
public class AttributeMatcher {
  private final String attributeName;
  @Nullable private final String attributeValue;

  /**
   * Create instance used to match data point attribute with any value.
   *
   * @param attributeName matched attribute name
   */
  AttributeMatcher(String attributeName) {
    this(attributeName, null);
  }

  /**
   * Create instance used to match data point attribute with te same name and with the same value.
   *
   * @param attributeName attribute name
   * @param attributeValue attribute value
   */
  AttributeMatcher(String attributeName, @Nullable String attributeValue) {
    this.attributeName = attributeName;
    this.attributeValue = attributeValue;
  }

  /**
   * Return name of data point attribute that this AttributeMatcher is supposed to match value with.
   *
   * @return name of validated attribute
   */
  public String getAttributeName() {
    return attributeName;
  }

  @Override
  public String toString() {
    return attributeValue == null
        ? '{' + attributeName + '}'
        : '{' + attributeName + '=' + attributeValue + '}';
  }

  /**
   * Verify if this matcher is matching provided attribute value. If this matcher holds null value
   * then it is matching any attribute value.
   *
   * @param value a value to be matched
   * @return true if this matcher is matching provided value, false otherwise.
   */
  boolean matchesValue(String value) {
    return attributeValue == null || attributeValue.equals(value);
  }
}
