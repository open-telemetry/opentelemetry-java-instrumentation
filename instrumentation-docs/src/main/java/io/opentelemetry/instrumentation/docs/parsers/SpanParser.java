/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This class is responsible for parsing span files from the `.telemetry` directory of an
 * instrumentation module and filtering them by scope.
 */
public class SpanParser {

  // We want to ignore test related attributes
  private static final List<String> EXCLUDED_ATTRIBUTES = List.of("x-test-", "test-baggage-");

  /**
   * Pull spans from the `.telemetry` directory, filter them by scope, and set them in the module.
   *
   * @param module the instrumentation module to extract spans for
   * @param fileManager the file manager to access the filesystem
   * @throws JsonProcessingException if there is an error processing the JSON from the span files
   */
  public static Map<String, List<EmittedSpans.Span>> getSpans(
      InstrumentationModule module, FileManager fileManager) throws JsonProcessingException {
    Map<String, EmittedSpans> spans =
        EmittedSpanParser.getSpansByScopeFromFiles(fileManager.rootDir(), module.getSrcPath());

    if (spans.isEmpty()) {
      return new HashMap<>();
    }

    String scopeName = module.getScopeInfo().getName();
    return filterSpansByScope(spans, scopeName);
  }

  /**
   * Filters spans by scope and aggregates attributes for each span kind.
   *
   * @param spansByScope the map of spans by scope
   * @param scopeName the name of the scope to filter spans for
   * @return a map of filtered spans by `when`
   */
  private static Map<String, List<EmittedSpans.Span>> filterSpansByScope(
      Map<String, EmittedSpans> spansByScope, String scopeName) {

    Map<String, Map<String, Set<TelemetryAttribute>>> aggregatedAttributes = new HashMap<>();

    for (Map.Entry<String, EmittedSpans> entry : spansByScope.entrySet()) {
      if (!hasValidSpans(entry.getValue())) {
        continue;
      }

      String when = entry.getValue().getWhen();
      Map<String, Map<String, Set<TelemetryAttribute>>> result =
          SpanAggregator.aggregateSpans(when, entry.getValue(), scopeName);
      aggregatedAttributes.putAll(result);
    }

    return SpanAggregator.buildFilteredSpans(aggregatedAttributes);
  }

  private static boolean hasValidSpans(EmittedSpans spans) {
    return spans != null && spans.getSpansByScope() != null;
  }

  /** Helper class to aggregate span attributes by scope and kind. */
  static class SpanAggregator {

    public static Map<String, Map<String, Set<TelemetryAttribute>>> aggregateSpans(
        String when, EmittedSpans spans, String targetScopeName) {
      Map<String, Map<String, Set<TelemetryAttribute>>> aggregatedAttributes = new HashMap<>();
      Map<String, Set<TelemetryAttribute>> spanKindMap =
          aggregatedAttributes.computeIfAbsent(when, k -> new HashMap<>());

      for (EmittedSpans.SpansByScope spansByScope : spans.getSpansByScope()) {
        if (spansByScope.getScope().equals(targetScopeName)) {
          processSpansForScope(spansByScope, spanKindMap);
        }
      }
      return aggregatedAttributes;
    }

    private static void processSpansForScope(
        EmittedSpans.SpansByScope spansByScope, Map<String, Set<TelemetryAttribute>> spanKindMap) {
      for (EmittedSpans.Span span : spansByScope.getSpans()) {
        Set<TelemetryAttribute> attributes =
            spanKindMap.computeIfAbsent(span.getSpanKind(), k -> new HashSet<>());

        addSpanAttributes(span, attributes);
      }
    }

    private static void addSpanAttributes(
        EmittedSpans.Span span, Set<TelemetryAttribute> attributes) {
      if (span.getAttributes() == null) {
        return;
      }

      for (TelemetryAttribute attr : span.getAttributes()) {
        boolean excluded = EXCLUDED_ATTRIBUTES.stream().anyMatch(ex -> attr.getName().contains(ex));
        if (!excluded) {
          attributes.add(new TelemetryAttribute(attr.getName(), attr.getType()));
        }
      }
    }

    public static Map<String, List<EmittedSpans.Span>> buildFilteredSpans(
        Map<String, Map<String, Set<TelemetryAttribute>>> aggregatedAttributes) {
      Map<String, List<EmittedSpans.Span>> result = new HashMap<>();

      for (Map.Entry<String, Map<String, Set<TelemetryAttribute>>> entry :
          aggregatedAttributes.entrySet()) {
        String when = entry.getKey();
        List<EmittedSpans.Span> spans = result.computeIfAbsent(when, k -> new ArrayList<>());

        for (Map.Entry<String, Set<TelemetryAttribute>> kindEntry : entry.getValue().entrySet()) {
          String spanKind = kindEntry.getKey();
          Set<TelemetryAttribute> attributes = kindEntry.getValue();
          spans.add(new EmittedSpans.Span(spanKind, new ArrayList<>(attributes)));
        }
      }

      return result;
    }

    private SpanAggregator() {}
  }

  private SpanParser() {}
}
