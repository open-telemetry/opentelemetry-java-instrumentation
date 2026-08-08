/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IncludeExcludePredicateTest {

  @ParameterizedTest
  @MethodSource("patterns")
  void patternMatching(
      Collection<String> included, Collection<String> excluded, String value, boolean expected) {
    Predicate<String> predicate = IncludeExcludePredicate.createPatternMatching(included, excluded);

    assertThat(predicate.test(value)).isEqualTo(expected);
  }

  private static Stream<Arguments> patterns() {
    return Stream.of(
        argumentSet("empty patterns include all", emptyList(), emptyList(), "foo", true),
        argumentSet("exact include matches", singletonList("foo"), emptyList(), "foo", true),
        argumentSet("exact include rejects", singletonList("foo"), emptyList(), "bar", false),
        argumentSet(
            "star matches zero characters", singletonList("foo*"), emptyList(), "foo", true),
        argumentSet(
            "star matches many characters", singletonList("foo*"), emptyList(), "foobar", true),
        argumentSet(
            "question matches one character", singletonList("f?o"), emptyList(), "foo", true),
        argumentSet(
            "question rejects zero characters", singletonList("f?o"), emptyList(), "fo", false),
        argumentSet(
            "regex characters are literal",
            singletonList("f()[]$^.{}|*"),
            emptyList(),
            "f()[]$^.{}|oo",
            true),
        argumentSet(
            "matching exclude wins", singletonList("*"), singletonList("foo*"), "foobar", false),
        argumentSet(
            "nonmatching exclude allows", singletonList("*"), singletonList("foo*"), "bar", true),
        argumentSet("any include can match", asList("foo", "bar"), emptyList(), "bar", true));
  }
}
