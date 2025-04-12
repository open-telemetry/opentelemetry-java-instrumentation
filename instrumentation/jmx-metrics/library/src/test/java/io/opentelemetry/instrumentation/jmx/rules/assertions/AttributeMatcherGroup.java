/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.assertions;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** Group of attribute matchers */
public class AttributeMatcherGroup {

  // stored as a Map for easy lookup by name
  private final Map<String, AttributeMatcher> matchers;

  /**
   * Constructor for a set of attribute matchers
   *
   * @param matchers collection of matchers to build a group from
   * @throws IllegalStateException if there is any duplicate key
   */
  AttributeMatcherGroup(Collection<AttributeMatcher> matchers) {
    this.matchers =
        matchers.stream().collect(Collectors.toMap(AttributeMatcher::getAttributeName, m -> m));
  }

  /**
   * Checks if attributes match this attribute matcher group
   *
   * @param attributes attributes to check as map
   * @return {@literal true} when the attributes match all attributes from this group
   */
  public boolean matches(Map<String, String> attributes) {
    if (attributes.size() != matchers.size()) {
      return false;
    }

    for (Map.Entry<String, String> entry : attributes.entrySet()) {
      AttributeMatcher matcher = matchers.get(entry.getKey());
      if (matcher == null) {
        // no matcher for this key: unexpected key
        return false;
      }

      if (!matcher.matchesValue(entry.getValue())) {
        // value does not match: unexpected value
        return false;
      }
    }

    return true;
  }

  @Override
  public String toString() {
    return matchers.values().toString();
  }
}
