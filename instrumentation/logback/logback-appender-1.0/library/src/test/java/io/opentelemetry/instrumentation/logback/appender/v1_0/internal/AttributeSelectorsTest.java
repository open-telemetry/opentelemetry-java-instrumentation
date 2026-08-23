/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0.internal;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class AttributeSelectorsTest {

  @Test
  void emptySelectorSelectsNothing() {
    assertThat(AttributeSelectors.create(null)).isNull();
    assertThat(AttributeSelectors.create(IncludeExclude.builder().build())).isNull();
  }

  @Test
  void selectorMatchesGlobPatterns() {
    Predicate<String> selector =
        AttributeSelectors.create(
            IncludeExclude.builder()
                .setIncluded(asList("request-*", "user-?"))
                .setExcluded(singletonList("*-secret"))
                .build());

    assertThat(selector).isNotNull();
    assertThat(selector.test("request-id")).isTrue();
    assertThat(selector.test("user-1")).isTrue();
    assertThat(selector.test("user-name")).isFalse();
    assertThat(selector.test("request-secret")).isFalse();
    assertThat(selector.test("REQUEST-ID")).isFalse();
  }

  @Test
  void deprecatedBooleanSelectorSelectsEveryKeyWhenEnabled() {
    Predicate<String> selector = AttributeSelectors.createDeprecated(Boolean.TRUE);

    assertThat(selector).isNotNull();
    assertThat(selector.test("anything")).isTrue();
    assertThat(selector.test("")).isTrue();
  }

  @Test
  void deprecatedBooleanSelectorSelectsNothingWhenDisabledOrAbsent() {
    assertThat(AttributeSelectors.createDeprecated(Boolean.FALSE)).isNull();
    assertThat(AttributeSelectors.createDeprecated((Boolean) null)).isNull();
  }

  @Test
  void deprecatedSelectorIsEmptyWhenNoKeysAreConfigured() {
    assertThat(AttributeSelectors.createDeprecated(emptyList())).isNull();
  }

  @Test
  void deprecatedSelectorMatchesKeysLiterally() {
    Predicate<String> selector = AttributeSelectors.createDeprecated(asList("*", "key?", "userId"));

    assertThat(selector).isNotNull();
    assertThat(selector.test("*")).isTrue();
    assertThat(selector.test("key?")).isTrue();
    assertThat(selector.test("userId")).isTrue();
    assertThat(selector.test("key1")).isFalse();
    assertThat(selector.test("anything")).isFalse();
  }

  @Test
  void deprecatedSelectorMatchesEverythingWithSoleWildcard() {
    Predicate<String> selector = AttributeSelectors.createDeprecated(singletonList("*"));

    assertThat(selector).isNotNull();
    assertThat(selector.test("anything")).isTrue();
  }

  @Test
  void splitsCommaSeparatedValues() {
    assertThat(AttributeSelectors.split(null)).isEmpty();
    assertThat(AttributeSelectors.split("")).isEmpty();
    assertThat(AttributeSelectors.split(" , ")).isEmpty();
    assertThat(AttributeSelectors.split("key1, key2 ,,key3"))
        .containsExactly("key1", "key2", "key3");
  }
}
