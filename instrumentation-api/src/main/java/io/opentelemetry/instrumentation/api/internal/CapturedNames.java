/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.internal;

import static java.util.Collections.unmodifiableList;

import io.opentelemetry.instrumentation.api.config.IncludeExclude;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The names that an instrumentation captures from a carrier such as HTTP headers, gRPC metadata,
 * messaging headers or servlet request parameters, resolved either from an {@link IncludeExclude}
 * selector or, for the deprecated settings, from a list of exact names.
 *
 * <p>The two resolutions differ in what {@code *} and {@code ?} mean. In a selector they are glob
 * wildcards, while the deprecated settings never supported them as wildcards, so {@link
 * #createExact} matches their values literally. Interpreting those values as globs would silently
 * widen what is captured: a value of {@code "*"} that previously looked for a name literally called
 * {@code *}, and therefore captured nothing, would start capturing every name, including ones
 * holding credentials.
 *
 * <p>Whether names are matched case-insensitively is a property of the carrier, so it is resolved
 * here rather than by each caller. When it is, both the configured names and the names offered to
 * {@link #matchingNames(Iterable)} are lowercased, and every name returned by this class is
 * lowercase.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class CapturedNames {

  @Nullable private final IncludeExclude selector;
  // the exact names configured by a deprecated setting, which are matched literally
  @Nullable private final Set<String> exactOnlyNames;
  private final boolean caseInsensitive;
  // the names that the selector includes literally, which are looked up directly so that getters
  // which do not enumerate names keep working
  private final List<String> exactNames;
  // whether the selector can match names that are not listed in exactNames, which requires
  // enumerating the names carried by each request
  private final boolean enumerateNames;

  /**
   * Creates the names captured by a selector, whose patterns are matched as globs.
   *
   * @param selector the selector, or {@code null} when nothing is configured to be captured
   */
  public static CapturedNames create(
      @Nullable IncludeExclude selector, CaseSensitivity caseSensitivity) {
    return new CapturedNames(
        selector == null || selector.isEmpty() ? null : selector, null, caseSensitivity);
  }

  /**
   * Creates the names captured by a deprecated setting, whose values are exact names rather than
   * glob patterns.
   *
   * @param names the exact names, or {@code null} when nothing is configured to be captured
   */
  public static CapturedNames createExact(
      @Nullable Collection<String> names, CaseSensitivity caseSensitivity) {
    if (names == null || names.isEmpty()) {
      return new CapturedNames(null, null, caseSensitivity);
    }
    Set<String> exactOnlyNames = new LinkedHashSet<>();
    for (String name : names) {
      exactOnlyNames.add(normalize(name, caseSensitivity));
    }
    return new CapturedNames(null, exactOnlyNames, caseSensitivity);
  }

  private CapturedNames(
      @Nullable IncludeExclude selector,
      @Nullable Set<String> exactOnlyNames,
      CaseSensitivity caseSensitivity) {
    this.caseInsensitive = caseSensitivity == CaseSensitivity.CASE_INSENSITIVE;
    this.selector = selector == null ? null : normalize(selector, caseSensitivity);
    this.exactOnlyNames = exactOnlyNames;

    Set<String> names = new LinkedHashSet<>();
    boolean enumerate = false;
    if (exactOnlyNames != null) {
      names.addAll(exactOnlyNames);
    } else if (this.selector != null) {
      List<String> included = this.selector.getIncluded();
      // a selector without included patterns matches every name that is not excluded
      enumerate = included.isEmpty();
      for (String pattern : included) {
        if (pattern.indexOf('*') != -1 || pattern.indexOf('?') != -1) {
          enumerate = true;
        } else if (matches(pattern)) {
          names.add(pattern);
        }
      }
    }
    this.exactNames = unmodifiableList(new ArrayList<>(names));
    this.enumerateNames = enumerate;
  }

  /** Returns whether nothing is configured to be captured. */
  public boolean isEmpty() {
    return selector == null && exactOnlyNames == null;
  }

  /**
   * Returns whether names that are not listed by {@link #exactNames()} can be captured, which
   * requires enumerating the names carried by each request and passing them to {@link
   * #matchingNames(Iterable)}.
   */
  public boolean enumerateNames() {
    return enumerateNames;
  }

  /**
   * Returns the captured names that are known without enumerating the names carried by a request,
   * so that they can be looked up directly.
   */
  public List<String> exactNames() {
    return exactNames;
  }

  /**
   * Returns the captured names among {@code enumeratedNames}, together with {@link #exactNames()}.
   */
  public Collection<String> matchingNames(Iterable<String> enumeratedNames) {
    Set<String> names = new LinkedHashSet<>(exactNames);
    for (String name : enumeratedNames) {
      String normalized = normalize(name);
      if (matches(normalized)) {
        names.add(normalized);
      }
    }
    return names;
  }

  private boolean matches(String normalizedName) {
    if (exactOnlyNames != null) {
      return exactOnlyNames.contains(normalizedName);
    }
    return selector != null && selector.matches(normalizedName);
  }

  private String normalize(String value) {
    return caseInsensitive ? value.toLowerCase(Locale.ROOT) : value;
  }

  private static String normalize(String value, CaseSensitivity caseSensitivity) {
    return caseSensitivity == CaseSensitivity.CASE_INSENSITIVE
        ? value.toLowerCase(Locale.ROOT)
        : value;
  }

  private static IncludeExclude normalize(
      IncludeExclude selector, CaseSensitivity caseSensitivity) {
    if (caseSensitivity == CaseSensitivity.CASE_SENSITIVE) {
      return selector;
    }
    return IncludeExclude.builder()
        .setIncluded(normalize(selector.getIncluded(), caseSensitivity))
        .setExcluded(normalize(selector.getExcluded(), caseSensitivity))
        .build();
  }

  private static List<String> normalize(List<String> values, CaseSensitivity caseSensitivity) {
    List<String> normalized = new ArrayList<>(values.size());
    for (String value : values) {
      normalized.add(normalize(value, caseSensitivity));
    }
    return normalized;
  }

  /**
   * Whether the names of a carrier are matched case-sensitively.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public enum CaseSensitivity {
    /** Names are matched exactly as they are configured and carried, e.g. servlet parameters. */
    CASE_SENSITIVE,
    /** Names are lowercased before they are matched, e.g. HTTP headers. */
    CASE_INSENSITIVE
  }
}
