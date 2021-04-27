/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import net.bytebuddy.description.NamedElement;
import net.bytebuddy.matcher.ElementMatcher;

public final class NameMatchers {

  /**
   * Matches a {@link NamedElement} for its exact name's membership of a set.
   *
   * @param names The expected names.
   * @param <T> The type of the matched object.
   * @return An element matcher checking if an element's exact name is a member of a set.
   */
  public static <T extends NamedElement> ElementMatcher.Junction<T> namedOneOf(String... names) {
    return new SetMatcher<>(true, names);
  }

  /**
   * Matches a {@link NamedElement} for its exact name's membership of a set.
   *
   * @param names The expected names.
   * @param <T> The type of the matched object.
   * @return An element matcher checking if an element's exact name is a member of a set.
   */
  public static <T extends NamedElement> ElementMatcher.Junction<T> namedOneOf(
      Collection<String> names) {
    return new SetMatcher<>(true, names);
  }

  /**
   * Matches a {@link NamedElement} for its exact name's absence from a set.
   *
   * @param names The expected names.
   * @param <T> The type of the matched object.
   * @return An element matcher checking if an element's exact name is absent from a set.
   */
  public static <T extends NamedElement> ElementMatcher.Junction<T> namedNoneOf(String... names) {
    return new SetMatcher<>(false, names);
  }

  private static class SetMatcher<T extends NamedElement>
      extends ElementMatcher.Junction.AbstractBase<T> {

    private final boolean include;
    private final Set<String> values;

    private SetMatcher(boolean include, String... values) {
      this.include = include;
      this.values = new HashSet<>(values.length * 2);
      // happens early in the program and don't want to allocate a list
      for (String value : values) {
        this.values.add(value);
      }
    }

    private SetMatcher(boolean include, Collection<String> values) {
      this.include = include;
      this.values = new HashSet<>(values.size() * 2);
      this.values.addAll(values);
    }

    @Override
    public boolean matches(T target) {
      boolean contained = values.contains(target.getActualName());
      return (include && contained) || (!include && !contained);
    }
  }
}
