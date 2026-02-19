/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.assertions;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/** Group of attribute matchers */
public class AttributeMatcherGroup {

  // stored as a Map for easy lookup by name
  private final Map<String, AttributeMatcher> matchers;
  @Nullable private Predicate<Map<String, String>> applicabilityPredicate;

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
    if (!isApplicableFor(attributes)) {
      return false;
    }

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

  /**
   * Define a predicate that is evaluated to detect if this group of attribute matchers is
   * applicable for given set of metric attributes. Some metrics may contain optional attributes. In
   * such a case it is common to define few attribute matcher groups that validate possible
   * attribute sets. In the following example let's consider metric with 3 possible attributes: A,
   * B, C. A and B are always present, C is optional. Two attribute matcher groups should be
   * created:
   *
   * <ol>
   *   <li>group validating A and B
   *   <li>group validating A, B and C
   * </ol>
   *
   * <p>To improve validation reliability it is strongly recommended to define applicability
   * predicate wherever possible.
   *
   * @param predicate a predicate function with parameter holding map of metric attributes
   * @return this instance
   * @see #isApplicableFor
   */
  public AttributeMatcherGroup applicableWhen(Predicate<Map<String, String>> predicate) {
    Objects.requireNonNull(predicate, "predicate must not be null");
    if (applicabilityPredicate != null) {
      throw new IllegalStateException("applicability predicate is already set");
    }
    applicabilityPredicate = predicate;
    return this;
  }

  /**
   * Evaluate applicability of the attribute matcher group for provided metric attributes.
   * Evaluation is performed by testing the predicate configured with {@link #applicableWhen}
   * method. If {@link #applicableWhen} was not called to configure the predicate then this method
   * will always return <code>true</code>
   *
   * @param attributes a map holding attributes of verified metric
   * @return predicate evaluation result, or <code>true</code> if predicate is not defined
   * @see #applicableWhen
   */
  public boolean isApplicableFor(Map<String, String> attributes) {
    if (applicabilityPredicate == null) {
      return true;
    }
    return applicabilityPredicate.test(attributes);
  }

  @Override
  public String toString() {
    return matchers.values().toString();
  }
}
