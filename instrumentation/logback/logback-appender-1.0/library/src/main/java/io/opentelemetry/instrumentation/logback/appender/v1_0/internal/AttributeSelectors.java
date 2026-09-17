/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.logback.appender.v1_0.internal;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Factories for the attribute key selectors used by the OpenTelemetry Logback appender.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class AttributeSelectors {

  /**
   * Returns a selector that matches the keys selected by {@code selector}, or {@code null} when
   * {@code selector} selects nothing because it is absent or empty.
   *
   * <p>Keys and selector patterns are matched case-sensitively. {@code ?} matches any single
   * character and {@code *} matches any number of characters, including none. Excluded patterns
   * take precedence over included patterns, and a selector with only excluded patterns matches
   * every key that it does not exclude.
   */
  @Nullable
  public static Predicate<String> create(@Nullable IncludeExclude selector) {
    return selector == null || selector.isEmpty() ? null : selector::matches;
  }

  /**
   * Returns a selector with the semantics of a deprecated boolean capture setting, which either
   * matches every key or, when it is absent or {@code false}, selects nothing.
   */
  @Nullable
  public static Predicate<String> createDeprecated(@Nullable Boolean captureEveryKey) {
    return Boolean.TRUE.equals(captureEveryKey) ? key -> true : null;
  }

  /**
   * Returns a selector with the semantics of the deprecated comma-separated capture list, or {@code
   * null} when {@code capturedKeys} is empty and therefore selects nothing.
   *
   * <p>A list whose only entry is {@code *} matches every key. In every other list each entry
   * matches only the key that it equals, including an entry that contains {@code *} or {@code ?},
   * because the deprecated setting never supported patterns.
   */
  @Nullable
  public static Predicate<String> createDeprecated(List<String> capturedKeys) {
    if (capturedKeys.isEmpty()) {
      return null;
    }
    if (capturedKeys.size() == 1 && "*".equals(capturedKeys.get(0))) {
      return key -> true;
    }
    Set<String> keys = new HashSet<>(capturedKeys);
    return keys::contains;
  }

  /** Splits a comma-separated configuration value, dropping blank entries. */
  public static List<String> split(@Nullable String value) {
    if (value == null) {
      return emptyList();
    }
    return Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(entry -> !entry.isEmpty())
        .collect(toList());
  }

  private AttributeSelectors() {}
}
