/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry.internal;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Matches metric names against exact names and trailing-wildcard prefixes.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class MetricNameFilter implements Predicate<String> {

  private final Set<String> exactNames;
  private final List<String> prefixes;

  public static MetricNameFilter create(List<String> patterns) {
    return new MetricNameFilter(patterns);
  }

  private MetricNameFilter(List<String> patterns) {
    exactNames = new HashSet<>();
    prefixes = new ArrayList<>();
    for (String pattern : patterns) {
      int wildcardIndex = pattern.indexOf('*');
      if (pattern.isEmpty()
          || (wildcardIndex >= 0
              && (wildcardIndex != pattern.length() - 1
                  || wildcardIndex != pattern.lastIndexOf('*')))) {
        throw new IllegalArgumentException(
            "Metric name pattern must be an exact name or a prefix ending in '*': " + pattern);
      }
      if (wildcardIndex >= 0) {
        prefixes.add(pattern.substring(0, wildcardIndex));
      } else {
        exactNames.add(pattern);
      }
    }
  }

  @Override
  public boolean test(String metricName) {
    if (exactNames.contains(metricName)) {
      return true;
    }
    for (String prefix : prefixes) {
      if (metricName.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
