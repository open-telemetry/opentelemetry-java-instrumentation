/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.instrumenter;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import java.util.List;

/**
 * A filter that determines whether a span should be started for the given operation.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@FunctionalInterface
public interface ShouldStartFilter<REQUEST> {

  /**
   * Determines whether a span should be started for the given operation.
   *
   * @param parentContext the parent context of the operation
   * @param request the request object of the operation
   * @param spanKind the span kind that would be created
   * @param instrumentationName the name of the instrumentation
   * @return {@code true} if the span should be started, {@code false} otherwise
   */
  boolean shouldStart(
      Context parentContext, REQUEST request, SpanKind spanKind, String instrumentationName);

  /**
   * Returns the priority of this filter. Filters with lower numbers have higher priority and are
   * executed first.
   *
   * @return the priority of this filter, defaults to 0
   */
  default int getPriority() {
    return 0;
  }

  /**
   * Returns a filter that always allows spans to be started.
   *
   * @return a pass-through filter
   */
  static <REQUEST> ShouldStartFilter<REQUEST> none() {
    return (parentContext, request, spanKind, instrumentationName) -> true;
  }

  /** Combines multiple filters into a single composite filter. */
  static <REQUEST> ShouldStartFilter<REQUEST> allOf(List<ShouldStartFilter<REQUEST>> filters) {
    if (filters.isEmpty()) {
      return none();
    }
    if (filters.size() == 1) {
      return filters.get(0);
    }

    List<ShouldStartFilter<REQUEST>> sortedFilters =
        filters.stream()
            .sorted((f1, f2) -> Integer.compare(f1.getPriority(), f2.getPriority()))
            .collect(java.util.stream.Collectors.toList());

    return (parentContext, request, spanKind, instrumentationName) -> {
      for (ShouldStartFilter<REQUEST> filter : sortedFilters) {
        if (!filter.shouldStart(parentContext, request, spanKind, instrumentationName)) {
          return false;
        }
      }
      return true;
    };
  }
}
