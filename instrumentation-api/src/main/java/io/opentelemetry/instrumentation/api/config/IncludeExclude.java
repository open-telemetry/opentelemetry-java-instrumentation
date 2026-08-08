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
import java.util.regex.Pattern;

/**
 * An immutable selector that matches strings against included and excluded glob patterns.
 *
 * <p>Matching is case-sensitive. {@code ?} matches any single character and {@code *} matches any
 * number of characters, including none. Excluded patterns take precedence over included patterns.
 * When there are no included patterns, all values that are not excluded match. Callers are
 * responsible for normalizing values and patterns when a domain requires case-insensitive matching.
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

  /** Returns whether {@code value} matches this selector. */
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

    Pattern pattern = toRegexPattern(globPattern);
    return value -> pattern.matcher(value).matches();
  }

  private static Pattern toRegexPattern(String globPattern) {
    StringBuilder regex = new StringBuilder();
    int tokenStart = 0;
    for (int i = 0; i < globPattern.length(); i++) {
      char character = globPattern.charAt(i);
      if (character != '*' && character != '?') {
        continue;
      }

      if (tokenStart < i) {
        regex.append(Pattern.quote(globPattern.substring(tokenStart, i)));
      }
      regex.append(character == '*' ? ".*" : ".");
      tokenStart = i + 1;
    }
    if (tokenStart < globPattern.length()) {
      regex.append(Pattern.quote(globPattern.substring(tokenStart)));
    }
    return Pattern.compile(regex.toString());
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
