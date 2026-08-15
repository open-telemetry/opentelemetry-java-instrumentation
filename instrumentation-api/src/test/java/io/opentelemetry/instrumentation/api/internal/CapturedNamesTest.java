/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static io.opentelemetry.instrumentation.api.internal.CapturedNames.CaseSensitivity.CASE_INSENSITIVE;
import static io.opentelemetry.instrumentation.api.internal.CapturedNames.CaseSensitivity.CASE_SENSITIVE;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CapturedNamesTest {

  @Test
  void deprecatedWildcardValueCapturesNothing() {
    CapturedNames captured = CapturedNames.createExact(singletonList("*"), CASE_SENSITIVE);

    assertThat(captured.isEmpty()).isFalse();
    assertThat(captured.enumerateNames()).isFalse();
    assertThat(captured.exactNames()).containsExactly("*");
    assertThat(capture(captured, "authorization", "x-foo")).isEmpty();
  }

  @Test
  void deprecatedWildcardValueCapturesNameLiterallyCalledWildcard() {
    CapturedNames captured = CapturedNames.createExact(singletonList("*"), CASE_SENSITIVE);

    assertThat(capture(captured, "authorization", "*")).containsExactly("*");
  }

  @Test
  void selectorWildcardPatternCapturesEverything() {
    CapturedNames captured = CapturedNames.create(selector(singletonList("*")), CASE_SENSITIVE);

    assertThat(captured.enumerateNames()).isTrue();
    assertThat(captured.exactNames()).isEmpty();
    assertThat(capture(captured, "authorization", "x-foo"))
        .containsExactly("authorization", "x-foo");
  }

  @Test
  void deprecatedValuesAreMatchedLiterally() {
    CapturedNames captured =
        CapturedNames.createExact(asList("x-*", "x-fo?", "x-foo"), CASE_SENSITIVE);

    assertThat(capture(captured, "x-foo", "x-fob", "x-bar")).containsExactly("x-foo");
  }

  @Test
  void selectorPatternsAreMatchedAsGlobs() {
    CapturedNames captured = CapturedNames.create(selector(asList("x-*", "y-fo?")), CASE_SENSITIVE);

    assertThat(capture(captured, "x-foo", "y-fob", "y-fooo", "z-bar"))
        .containsExactly("x-foo", "y-fob");
  }

  @Test
  void caseInsensitiveLowercasesSelectorPatternsAndNames() {
    CapturedNames captured =
        CapturedNames.create(selector(asList("X-Foo", "Y-*")), CASE_INSENSITIVE);

    assertThat(captured.exactNames()).containsExactly("x-foo");
    assertThat(capture(captured, "x-foo", "y-bar")).containsExactly("x-foo", "y-bar");
  }

  @Test
  void caseInsensitiveLowercasesDeprecatedValues() {
    CapturedNames captured = CapturedNames.createExact(singletonList("X-Foo"), CASE_INSENSITIVE);

    assertThat(captured.exactNames()).containsExactly("x-foo");
  }

  @Test
  void caseInsensitiveLowercasesEnumeratedNames() {
    CapturedNames captured = CapturedNames.create(selector(singletonList("x-*")), CASE_INSENSITIVE);

    assertThat(captured.matchingNames(singletonList("X-Foo"))).containsExactly("x-foo");
  }

  @Test
  void caseSensitiveDoesNotMatchDifferentCasing() {
    CapturedNames captured = CapturedNames.create(selector(asList("X-Foo", "Y-*")), CASE_SENSITIVE);

    assertThat(captured.exactNames()).containsExactly("X-Foo");
    assertThat(capture(captured, "x-foo", "y-bar")).isEmpty();
    assertThat(capture(captured, "X-Foo", "Y-Bar")).containsExactly("X-Foo", "Y-Bar");
  }

  @Test
  void caseSensitiveDeprecatedValuesDoNotMatchDifferentCasing() {
    CapturedNames captured = CapturedNames.createExact(singletonList("X-Foo"), CASE_SENSITIVE);

    assertThat(captured.exactNames()).containsExactly("X-Foo");
    assertThat(capture(captured, "x-foo")).isEmpty();
  }

  @Test
  void excludedPatternsTakePrecedence() {
    CapturedNames captured =
        CapturedNames.create(
            IncludeExclude.builder()
                .setIncluded(asList("x-*", "x-secret"))
                .setExcluded(singletonList("x-secret"))
                .build(),
            CASE_SENSITIVE);

    // an included name that is also excluded is not looked up directly
    assertThat(captured.exactNames()).isEmpty();
    assertThat(capture(captured, "x-foo", "x-secret")).containsExactly("x-foo");
  }

  @Test
  void excludedPatternsAreMatchedCaseInsensitively() {
    CapturedNames captured =
        CapturedNames.create(
            IncludeExclude.builder()
                .setIncluded(singletonList("X-*"))
                .setExcluded(singletonList("X-Secret"))
                .build(),
            CASE_INSENSITIVE);

    assertThat(capture(captured, "x-foo", "x-secret")).containsExactly("x-foo");
  }

  @Test
  void excludeOnlySelectorEnumeratesNames() {
    CapturedNames captured =
        CapturedNames.create(
            IncludeExclude.builder().setExcluded(singletonList("authorization")).build(),
            CASE_SENSITIVE);

    assertThat(captured.isEmpty()).isFalse();
    assertThat(captured.enumerateNames()).isTrue();
    assertThat(captured.exactNames()).isEmpty();
    assertThat(capture(captured, "authorization", "x-foo")).containsExactly("x-foo");
  }

  @Test
  void literalSelectorDoesNotEnumerateNames() {
    CapturedNames captured =
        CapturedNames.create(selector(asList("x-foo", "x-bar")), CASE_SENSITIVE);

    assertThat(captured.enumerateNames()).isFalse();
    assertThat(captured.exactNames()).containsExactly("x-foo", "x-bar");
  }

  @Test
  void deprecatedValuesDoNotEnumerateNames() {
    CapturedNames captured = CapturedNames.createExact(asList("x-*", "x-foo"), CASE_SENSITIVE);

    assertThat(captured.enumerateNames()).isFalse();
    assertThat(captured.exactNames()).containsExactly("x-*", "x-foo");
  }

  @Test
  void nullSelectorCapturesNothing() {
    CapturedNames captured = CapturedNames.create(null, CASE_SENSITIVE);

    assertThat(captured.isEmpty()).isTrue();
    assertThat(captured.enumerateNames()).isFalse();
    assertThat(captured.exactNames()).isEmpty();
    assertThat(capture(captured, "x-foo")).isEmpty();
  }

  @Test
  void emptySelectorCapturesNothing() {
    CapturedNames captured = CapturedNames.create(IncludeExclude.builder().build(), CASE_SENSITIVE);

    assertThat(captured.isEmpty()).isTrue();
    assertThat(captured.enumerateNames()).isFalse();
    assertThat(capture(captured, "x-foo")).isEmpty();
  }

  @Test
  void nullAndEmptyDeprecatedValuesCaptureNothing() {
    assertThat(CapturedNames.createExact(null, CASE_SENSITIVE).isEmpty()).isTrue();
    assertThat(CapturedNames.createExact(emptyList(), CASE_SENSITIVE).isEmpty()).isTrue();
  }

  @Test
  void duplicateNamesAreDeduplicated() {
    CapturedNames captured =
        CapturedNames.createExact(asList("X-Foo", "x-foo", "x-bar"), CASE_INSENSITIVE);

    assertThat(captured.exactNames()).containsExactly("x-foo", "x-bar");
  }

  @Test
  void matchingNamesIncludesExactNamesThatAreNotEnumerated() {
    CapturedNames captured = CapturedNames.create(selector(asList("x-foo", "y-*")), CASE_SENSITIVE);

    assertThat(captured.matchingNames(singletonList("y-bar"))).containsExactly("x-foo", "y-bar");
  }

  private static IncludeExclude selector(List<String> included) {
    return IncludeExclude.builder().setIncluded(included).build();
  }

  /**
   * Captures from a carrier that holds {@code carrierNames}, the way an attributes extractor does:
   * enumerating its names only when the resolved selector requires it, and otherwise looking up
   * each exact name directly.
   */
  private static List<String> capture(CapturedNames captured, String... carrierNames) {
    Set<String> carrier = new LinkedHashSet<>(asList(carrierNames));
    if (captured.isEmpty()) {
      return emptyList();
    }
    Collection<String> names =
        captured.enumerateNames() ? captured.matchingNames(carrier) : captured.exactNames();
    List<String> result = new ArrayList<>();
    for (String name : names) {
      if (carrier.contains(name)) {
        result.add(name);
      }
    }
    return result;
  }
}
