/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * A predicate that evaluates whether a string matches configurable include and exclude glob
 * patterns.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class IncludeExcludePredicate implements Predicate<String> {

  private final List<Predicate<String>> included;
  private final List<Predicate<String>> excluded;

  /**
   * Creates a case-sensitive pattern matching include/exclude predicate. Excludes take precedence
   * over includes. {@code *} matches zero or more characters and {@code ?} matches one character.
   */
  public static Predicate<String> createPatternMatching(
      @Nullable Collection<String> included, @Nullable Collection<String> excluded) {
    return new IncludeExcludePredicate(included, excluded);
  }

  private IncludeExcludePredicate(
      @Nullable Collection<String> included, @Nullable Collection<String> excluded) {
    this.included = createGlobPredicates(included);
    this.excluded = createGlobPredicates(excluded);
  }

  @Override
  public boolean test(String value) {
    return (included.isEmpty() || matchesAny(included, value)) && !matchesAny(excluded, value);
  }

  private static List<Predicate<String>> createGlobPredicates(
      @Nullable Collection<String> patterns) {
    if (patterns == null || patterns.isEmpty()) {
      return emptyList();
    }

    List<Predicate<String>> predicates = new ArrayList<>(patterns.size());
    for (String pattern : patterns) {
      predicates.add(createGlobPredicate(pattern));
    }
    return predicates;
  }

  private static Predicate<String> createGlobPredicate(String globPattern) {
    if (globPattern.equals("*")) {
      return value -> true;
    }
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
