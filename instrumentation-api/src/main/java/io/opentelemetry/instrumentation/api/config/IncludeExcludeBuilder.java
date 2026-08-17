/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.config;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** A builder for {@link IncludeExclude}. */
public final class IncludeExcludeBuilder {

  private List<String> included = emptyList();
  private List<String> excluded = emptyList();

  IncludeExcludeBuilder() {}

  /** Replaces the included patterns. */
  @CanIgnoreReturnValue
  public IncludeExcludeBuilder setIncluded(Collection<String> included) {
    this.included = copyPatterns(included, "included");
    return this;
  }

  /** Replaces the included patterns. */
  @CanIgnoreReturnValue
  public IncludeExcludeBuilder setIncluded(String... included) {
    return setIncluded(asList(included));
  }

  /** Replaces the excluded patterns. */
  @CanIgnoreReturnValue
  public IncludeExcludeBuilder setExcluded(Collection<String> excluded) {
    this.excluded = copyPatterns(excluded, "excluded");
    return this;
  }

  /** Replaces the excluded patterns. */
  @CanIgnoreReturnValue
  public IncludeExcludeBuilder setExcluded(String... excluded) {
    return setExcluded(asList(excluded));
  }

  /** Returns a new immutable {@link IncludeExclude}. */
  public IncludeExclude build() {
    return new IncludeExclude(included, excluded);
  }

  private static List<String> copyPatterns(Collection<String> patterns, String name) {
    requireNonNull(patterns, name);
    List<String> copy = new ArrayList<>(patterns.size());
    for (String pattern : patterns) {
      copy.add(requireNonNull(pattern, name + " pattern"));
    }
    return copy;
  }
}
