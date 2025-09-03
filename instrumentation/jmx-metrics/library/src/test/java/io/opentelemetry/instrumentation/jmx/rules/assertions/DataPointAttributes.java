/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.assertions;

import java.util.Arrays;

/**
 * Utility class implementing convenience static methods to construct data point attribute matchers
 * and sets of matchers.
 */
public class DataPointAttributes {
  private DataPointAttributes() {}

  /**
   * Create instance of matcher that should be used to check if data point attribute with given name
   * has value identical to the one provided as a parameter (exact match).
   *
   * @param name name of the data point attribute to check
   * @param value expected value of checked data point attribute
   * @return instance of matcher
   */
  public static AttributeMatcher attribute(String name, String value) {
    return new AttributeMatcher(name, value);
  }

  /**
   * Create instance of matcher that should be used to check if data point attribute with given name
   * exists. Any value of the attribute is considered as matching (any value match).
   *
   * @param name name of the data point attribute to check
   * @return instance of matcher
   */
  public static AttributeMatcher attributeWithAnyValue(String name) {
    return new AttributeMatcher(name);
  }

  /**
   * Creates a group of attribute matchers that should be used to verify data point attributes.
   *
   * @param attributes list of matchers to create group. It must contain matchers with unique names.
   * @return group of attribute matchers
   * @throws IllegalArgumentException if provided list contains two or more matchers with the same
   *     attribute name
   * @see MetricAssert#hasDataPointsWithAttributes(AttributeMatcherGroup...) for detailed
   *     description off the algorithm used for matching
   */
  public static AttributeMatcherGroup attributeGroup(AttributeMatcher... attributes) {
    return new AttributeMatcherGroup(Arrays.asList(attributes));
  }
}
