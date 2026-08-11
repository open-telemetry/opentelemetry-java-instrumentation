/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.nCopies;
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
            .setIncluded("second", "third")
            .setExcluded(singletonList("fourth"))
            .setExcluded("fifth", "sixth")
            .build();

    assertThat(selector.getIncluded()).containsExactly("second", "third");
    assertThat(selector.getExcluded()).containsExactly("fifth", "sixth");
  }

  @Test
  void selectorIsEmptyOnlyWithoutPatterns() {
    assertThat(IncludeExclude.builder().build().isEmpty()).isTrue();
    assertThat(IncludeExclude.builder().setIncluded(singletonList("included")).build().isEmpty())
        .isFalse();
    assertThat(IncludeExclude.builder().setExcluded(singletonList("excluded")).build().isEmpty())
        .isFalse();
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
    assertThatThrownBy(() -> IncludeExclude.builder().setIncluded((Collection<String>) null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> IncludeExclude.builder().setExcluded(asList("foo", null)))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> IncludeExclude.builder().setIncluded((String[]) null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> IncludeExclude.builder().setExcluded("foo", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void multiStarNonMatchHasBoundedRuntime() {
    String value = String.join("", nCopies(1_000, "a"));
    IncludeExclude selector =
        IncludeExclude.builder().setIncluded(singletonList("*a*a*a*a*a*a*a*a*a*a*b")).build();

    assertThat(selector.matches(value)).isFalse();
  }

  @Test
  void matchesAllSmallPatterns() {
    List<String> mismatches = new ArrayList<>();
    List<String> values = allStrings("ab", 5);
    for (String pattern : allStrings("ab*?", 5)) {
      IncludeExclude selector =
          IncludeExclude.builder().setIncluded(singletonList(pattern)).build();
      for (String value : values) {
        boolean expected = referenceAsciiGlobMatches(pattern, value);
        boolean actual = selector.matches(value);
        if (actual != expected) {
          mismatches.add(
              "pattern="
                  + pattern
                  + ", value="
                  + value
                  + ", expected="
                  + expected
                  + ", actual="
                  + actual);
        }
      }
    }

    assertThat(mismatches).isEmpty();
  }

  private static Stream<Arguments> patterns() {
    return Stream.of(
        argumentSet("empty patterns include all", emptyList(), emptyList(), "foo", true),
        argumentSet("exact include matches", singletonList("foo"), emptyList(), "foo", true),
        argumentSet("exact include rejects", singletonList("foo"), emptyList(), "bar", false),
        argumentSet("matching is case sensitive", singletonList("foo"), emptyList(), "FOO", false),
        argumentSet("star matches empty value", singletonList("*"), emptyList(), "", true),
        argumentSet(
            "star matches zero characters", singletonList("foo*"), emptyList(), "foo", true),
        argumentSet(
            "star matches many characters", singletonList("foo*"), emptyList(), "foobar", true),
        argumentSet(
            "star backtracks to match suffix",
            singletonList("f*bar"),
            emptyList(),
            "foobazbar",
            true),
        argumentSet(
            "multiple stars backtrack", singletonList("*ab*cd"), emptyList(), "xxabyycd", true),
        argumentSet("consecutive stars match", singletonList("f**o"), emptyList(), "foo", true),
        argumentSet(
            "trailing stars match empty", singletonList("foo***"), emptyList(), "foo", true),
        argumentSet(
            "wildcards require whole value", singletonList("foo*"), emptyList(), "xfoobar", false),
        argumentSet(
            "star cannot make missing suffix",
            singletonList("foo*bar"),
            emptyList(),
            "foobaz",
            false),
        argumentSet(
            "star matches line terminators", singletonList("foo*"), emptyList(), "foo\nbar", true),
        argumentSet(
            "star matches unicode characters",
            singletonList("f*o"),
            emptyList(),
            "f" + new String(Character.toChars(0x1F600)) + "o",
            true),
        argumentSet(
            "question matches one character", singletonList("f?o"), emptyList(), "foo", true),
        argumentSet(
            "question matches a line terminator", singletonList("f?o"), emptyList(), "f\no", true),
        argumentSet(
            "question matches one unicode character",
            singletonList("f?o"),
            emptyList(),
            "f" + new String(Character.toChars(0x1F600)) + "o",
            true),
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

  private static List<String> allStrings(String alphabet, int maxLength) {
    List<String> strings = new ArrayList<>();
    addStrings(strings, alphabet, maxLength, "");
    return strings;
  }

  private static void addStrings(
      List<String> strings, String alphabet, int maxLength, String prefix) {
    strings.add(prefix);
    if (prefix.length() == maxLength) {
      return;
    }
    for (int i = 0; i < alphabet.length(); i++) {
      addStrings(strings, alphabet, maxLength, prefix + alphabet.charAt(i));
    }
  }

  private static boolean referenceAsciiGlobMatches(String pattern, String value) {
    boolean[][] matches = new boolean[pattern.length() + 1][value.length() + 1];
    matches[0][0] = true;
    for (int patternIndex = 1; patternIndex <= pattern.length(); patternIndex++) {
      char patternChar = pattern.charAt(patternIndex - 1);
      if (patternChar == '*') {
        matches[patternIndex][0] = matches[patternIndex - 1][0];
      }
      for (int valueIndex = 1; valueIndex <= value.length(); valueIndex++) {
        if (patternChar == '*') {
          matches[patternIndex][valueIndex] =
              matches[patternIndex - 1][valueIndex] || matches[patternIndex][valueIndex - 1];
        } else if (patternChar == '?' || patternChar == value.charAt(valueIndex - 1)) {
          matches[patternIndex][valueIndex] = matches[patternIndex - 1][valueIndex - 1];
        }
      }
    }
    return matches[pattern.length()][value.length()];
  }
}
