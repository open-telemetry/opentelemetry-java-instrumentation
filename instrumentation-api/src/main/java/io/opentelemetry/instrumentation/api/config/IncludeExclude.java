/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * An immutable selector that matches strings against included and excluded glob patterns.
 *
 * <p>Matching is case-sensitive. {@code ?} matches any single character and {@code *} matches any
 * number of characters, including none. Excluded patterns take precedence over included patterns.
 * When there are no included patterns, all values that are not excluded match. Callers are
 * responsible for normalizing values and patterns when a domain requires case-insensitive matching.
 *
 * <p>An empty selector, one with no included and no excluded patterns, carries no configuration. A
 * setting that uses one should behave as if no selector were configured and fall back to its own
 * default.
 */
public final class IncludeExclude {

  private final List<String> included;
  private final List<String> excluded;
  private final List<Predicate<String>> includedPredicates;
  private final List<Predicate<String>> excludedPredicates;

  /** Returns a new builder for an {@link IncludeExclude}. */
  public static IncludeExcludeBuilder builder() {
    return new IncludeExcludeBuilder();
  }

  IncludeExclude(List<String> included, List<String> excluded) {
    this.included = unmodifiableList(new ArrayList<>(included));
    this.excluded = unmodifiableList(new ArrayList<>(excluded));
    this.includedPredicates = createGlobPredicates(included);
    this.excludedPredicates = createGlobPredicates(excluded);
  }

  /** Returns the included patterns. */
  public List<String> getIncluded() {
    return included;
  }

  /** Returns the excluded patterns. */
  public List<String> getExcluded() {
    return excluded;
  }

  /**
   * Returns whether this selector has no included and no excluded patterns.
   *
   * <p>An empty selector carries no configuration, so a setting that uses one should fall back to
   * its own default, as if no selector were configured.
   */
  public boolean isEmpty() {
    return included.isEmpty() && excluded.isEmpty();
  }

  /**
   * Returns whether {@code value} matches this selector.
   *
   * <p>Excluded patterns take precedence over included patterns: an excluded value never matches,
   * even when an included pattern also matches it. When there are no included patterns, every value
   * that is not excluded matches, so an {@linkplain #isEmpty() empty} selector matches every value.
   * A caller that treats an empty selector as no configuration should check {@link #isEmpty()}
   * rather than call this method.
   */
  public boolean matches(String value) {
    requireNonNull(value, "value");
    return (includedPredicates.isEmpty() || matchesAny(includedPredicates, value))
        && !matchesAny(excludedPredicates, value);
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof IncludeExclude)) {
      return false;
    }
    IncludeExclude that = (IncludeExclude) object;
    return included.equals(that.included) && excluded.equals(that.excluded);
  }

  @Override
  public int hashCode() {
    return Objects.hash(included, excluded);
  }

  @Override
  public String toString() {
    return "IncludeExclude{included=" + included + ", excluded=" + excluded + '}';
  }

  private static List<Predicate<String>> createGlobPredicates(List<String> patterns) {
    if (patterns.isEmpty()) {
      return emptyList();
    }

    List<Predicate<String>> predicates = new ArrayList<>(patterns.size());
    for (String pattern : patterns) {
      predicates.add(createGlobPredicate(pattern));
    }
    return unmodifiableList(predicates);
  }

  private static Predicate<String> createGlobPredicate(String globPattern) {
    if (globPattern.indexOf('*') == -1 && globPattern.indexOf('?') == -1) {
      return globPattern::equals;
    }

    return value -> globMatches(globPattern, value);
  }

  private static boolean globMatches(String pattern, String value) {
    int patternIndex = 0;
    int valueIndex = 0;
    int patternIndexAfterStar = -1;
    int valueIndexAfterStar = -1;

    // Do not translate globs to regular expressions: repeated '*' segments can cause excessive
    // regex backtracking on a non-match. Possessive quantifiers change glob semantics because '*'
    // may need to give characters back to match a later token (for example, glob "*a" must match
    // "ba"). Each retry below consumes one value code point, bounding the work by the product of
    // the pattern and value lengths.
    while (valueIndex < value.length()) {
      if (patternIndex < pattern.length()) {
        int patternCodePoint = pattern.codePointAt(patternIndex);
        if (patternCodePoint == '*') {
          patternIndex += Character.charCount(patternCodePoint);
          patternIndexAfterStar = patternIndex;
          valueIndexAfterStar = valueIndex;
          continue;
        }

        int valueCodePoint = value.codePointAt(valueIndex);
        if (patternCodePoint == '?' || patternCodePoint == valueCodePoint) {
          patternIndex += Character.charCount(patternCodePoint);
          valueIndex += Character.charCount(valueCodePoint);
          continue;
        }
      }

      if (patternIndexAfterStar == -1) {
        return false;
      }

      int valueCodePoint = value.codePointAt(valueIndexAfterStar);
      valueIndexAfterStar += Character.charCount(valueCodePoint);
      valueIndex = valueIndexAfterStar;
      patternIndex = patternIndexAfterStar;
    }

    while (patternIndex < pattern.length() && pattern.codePointAt(patternIndex) == '*') {
      patternIndex++;
    }
    return patternIndex == pattern.length();
  }

  private static boolean matchesAny(List<Predicate<String>> predicates, String value) {
    for (Predicate<String> predicate : predicates) {
      if (predicate.test(value)) {
        return true;
      }
    }
    return false;
  }
}
