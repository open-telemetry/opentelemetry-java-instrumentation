/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class IncludeExcludeTest {

  @ParameterizedTest
  @MethodSource("patterns")
  void patternMatching(
      Collection<String> included, Collection<String> excluded, String value, boolean expected) {
    IncludeExclude selector =
        IncludeExclude.builder().setIncluded(included).setExcluded(excluded).build();

    assertThat(selector.matches(value)).isEqualTo(expected);
  }

  @Test
  void builderReplacesPatterns() {
    IncludeExclude selector =
        IncludeExclude.builder()
            .setIncluded(singletonList("first"))
            .setIncluded(singletonList("second"))
            .setExcluded(singletonList("third"))
            .setExcluded(singletonList("fourth"))
            .build();

    assertThat(selector.getIncluded()).containsExactly("second");
    assertThat(selector.getExcluded()).containsExactly("fourth");
  }

  @Test
  void selectorIsImmutable() {
    List<String> included = new ArrayList<>(singletonList("included"));
    List<String> excluded = new ArrayList<>(singletonList("excluded"));
    IncludeExclude selector =
        IncludeExclude.builder().setIncluded(included).setExcluded(excluded).build();

    included.add("later included");
    excluded.add("later excluded");

    assertThat(selector.getIncluded()).containsExactly("included");
    assertThat(selector.getExcluded()).containsExactly("excluded");
    assertThatThrownBy(() -> selector.getIncluded().add("other"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> selector.getExcluded().add("other"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsNullPatterns() {
    assertThatThrownBy(() -> IncludeExclude.builder().setIncluded(null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> IncludeExclude.builder().setExcluded(asList("foo", null)))
        .isInstanceOf(NullPointerException.class);
  }

  private static Stream<Arguments> patterns() {
    return Stream.of(
        argumentSet("empty patterns include all", emptyList(), emptyList(), "foo", true),
        argumentSet("exact include matches", singletonList("foo"), emptyList(), "foo", true),
        argumentSet("exact include rejects", singletonList("foo"), emptyList(), "bar", false),
        argumentSet("matching is case sensitive", singletonList("foo"), emptyList(), "FOO", false),
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
            "exclude-only selector includes nonmatches",
            emptyList(),
            singletonList("foo*"),
            "bar",
            true),
        argumentSet("any include can match", asList("foo", "bar"), emptyList(), "bar", true));
  }
}
