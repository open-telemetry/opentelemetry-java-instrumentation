/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Converts the values of a deprecated include-only capture setting into a selector.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class DeprecatedCaptureNames {

  private static final Logger logger = Logger.getLogger(DeprecatedCaptureNames.class.getName());
  private static final Set<String> warnings = ConcurrentHashMap.newKeySet();

  /**
   * Returns a selector matching {@code names}, ignoring every name that contains {@code *} or
   * {@code ?}, or {@code null} when no name remains.
   *
   * <p>The deprecated include-only settings match names literally, so a value containing a glob
   * metacharacter only ever matched a name containing that character literally, which in practice
   * matched nothing. Interpreting such a value as a glob pattern would silently widen what is
   * captured, turning a value of {@code "*"} that captured nothing into one that captures
   * everything, including names holding credentials. Such values are ignored and logged instead.
   *
   * <p>Because no remaining name contains a metacharacter, {@link IncludeExclude} matches them
   * literally, so the returned selector captures exactly what this setting captured for the names
   * it keeps.
   *
   * <p>{@code null} is returned rather than an empty selector because an empty selector matches
   * every name.
   *
   * @param source what configured the names, named in the warning
   * @param replacement what selects names by glob pattern instead, or {@code null} when there is no
   *     replacement for {@code source}
   */
  @Nullable
  public static IncludeExclude toSelector(
      @Nullable Collection<String> names, String source, @Nullable String replacement) {
    if (names == null || names.isEmpty()) {
      return null;
    }

    List<String> exactNames = new ArrayList<>(names.size());
    List<String> ignoredNames = new ArrayList<>();
    for (String name : names) {
      if (name.indexOf('*') >= 0 || name.indexOf('?') >= 0) {
        ignoredNames.add(name);
      } else {
        exactNames.add(name);
      }
    }

    if (!ignoredNames.isEmpty() && warnings.add(source)) {
      logger.warning(
          "Ignoring "
              + ignoredNames
              + " configured in "
              + source
              + ", which matches names literally and never supported wildcards."
              + (replacement == null ? "" : " Use " + replacement + " to match names by pattern."));
    }

    return exactNames.isEmpty() ? null : IncludeExclude.builder().setIncluded(exactNames).build();
  }

  /**
   * Returns a selector matching {@code names}, ignoring every name that contains {@code *} or
   * {@code ?}, or an {@linkplain IncludeExclude#isEmpty() empty} selector when no name remains. See
   * {@link #toSelector} for why such names are ignored.
   *
   * <p>This is the variant for settings that always hold a selector rather than an optional one, so
   * that a setting whose every value was ignored captures nothing.
   */
  public static IncludeExclude toSelectorOrEmpty(
      @Nullable Collection<String> names, String source, @Nullable String replacement) {
    IncludeExclude selector = toSelector(names, source, replacement);
    return selector == null ? IncludeExclude.builder().build() : selector;
  }

  private DeprecatedCaptureNames() {}
}
