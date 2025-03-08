/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.util.internal;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Utilities for glob pattern matching.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class GlobUtil {

  private GlobUtil() {}

  /**
   * Return a predicate that returns {@code true} if a string matches the {@code globPattern}.
   *
   * <p>{@code globPattern} may contain the wildcard characters {@code *} and {@code ?} with the
   * following matching criteria:
   *
   * <ul>
   *   <li>{@code *} matches 0 or more instances of any character
   *   <li>{@code ?} matches exactly one instance of any character
   * </ul>
   */
  public static Predicate<String> toGlobPatternPredicate(String globPattern) {
    // Match all
    if (globPattern.equals("*")) {
      return unused -> true;
    }

    // If globPattern contains '*' or '?', convert it to a regex and return corresponding predicate
    for (int i = 0; i < globPattern.length(); i++) {
      char c = globPattern.charAt(i);
      if (c == '*' || c == '?') {
        Pattern pattern = toRegexPattern(globPattern);
        return string -> pattern.matcher(string).matches();
      }
    }

    // Exact match, ignoring case
    return globPattern::equalsIgnoreCase;
  }

  /**
   * Transform the {@code globPattern} to a regex by converting {@code *} to {@code .*}, {@code ?}
   * to {@code .}, and escaping other regex special characters.
   */
  private static Pattern toRegexPattern(String globPattern) {
    int tokenStart = -1;
    StringBuilder patternBuilder = new StringBuilder();
    for (int i = 0; i < globPattern.length(); i++) {
      char c = globPattern.charAt(i);
      if (c == '*' || c == '?') {
        if (tokenStart != -1) {
          patternBuilder.append(Pattern.quote(globPattern.substring(tokenStart, i)));
          tokenStart = -1;
        }
        if (c == '*') {
          patternBuilder.append(".*");
        } else {
          // c == '?'
          patternBuilder.append(".");
        }
      } else {
        if (tokenStart == -1) {
          tokenStart = i;
        }
      }
    }
    if (tokenStart != -1) {
      patternBuilder.append(Pattern.quote(globPattern.substring(tokenStart)));
    }
    return Pattern.compile(patternBuilder.toString());
  }
}
