/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.YamlHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * This class is responsible for parsing span-* files from the `.telemetry` directory of an
 * instrumentation module and converting them into the {@link EmittedSpans} format.
 */
public class EmittedSpanParser {
  private static final Logger logger = Logger.getLogger(EmittedSpanParser.class.getName());

  /**
   * Looks for span files in the .telemetry directory, and combines them into a single map.
   *
   * @param instrumentationDirectory the directory to traverse
   * @return contents of aggregated files
   */
  public static Map<String, EmittedSpans> getSpansByScopeFromFiles(
      String rootDir, String instrumentationDirectory) throws JsonProcessingException {
    Map<String, StringBuilder> spansByScope = new HashMap<>();
    Path telemetryDir = Paths.get(rootDir + "/" + instrumentationDirectory, ".telemetry");

    if (Files.exists(telemetryDir) && Files.isDirectory(telemetryDir)) {
      try (Stream<Path> files = Files.list(telemetryDir)) {
        files
            .filter(path -> path.getFileName().toString().startsWith("spans-"))
            .forEach(
                path -> {
                  String content = FileManager.readFileToString(path.toString());
                  if (content != null) {
                    String when = content.substring(0, content.indexOf('\n'));
                    String whenKey = when.replace("when: ", "");

                    spansByScope.putIfAbsent(whenKey, new StringBuilder("spans_by_scope:\n"));

                    // Skip the spans_by_scope line so we can aggregate into one list
                    int spanIndex = content.indexOf("spans_by_scope:\n");
                    if (spanIndex != -1) {
                      String contentAfter =
                          content.substring(spanIndex + "spans_by_scope:\n".length());
                      spansByScope.get(whenKey).append(contentAfter);
                    }
                  }
                });
      } catch (IOException e) {
        logger.severe("Error reading span files: " + e.getMessage());
      }
    }

    return parseSpans(spansByScope);
  }

  /**
   * Takes in a raw string representation of the aggregated EmittedSpan yaml map, separated by the
   * `when`, indicating the conditions under which the telemetry is emitted. deduplicates by name
   * and then returns a new map.
   *
   * @param input raw string representation of EmittedSpans yaml
   * @return {@code Map<String, EmittedSpans>} where the key is the `when` condition
   */
  private static Map<String, EmittedSpans> parseSpans(Map<String, StringBuilder> input)
      throws JsonProcessingException {
    Map<String, EmittedSpans> result = new HashMap<>();

    for (Map.Entry<String, StringBuilder> entry : input.entrySet()) {
      String when = entry.getKey().strip();
      StringBuilder content = entry.getValue();

      EmittedSpans spans = YamlHelper.emittedSpansParser(content.toString());
      if (spans.getSpansByScope().isEmpty()) {
        continue;
      }

      Map<String, Map<String, Set<TelemetryAttribute>>> attributesByScopeAndSpanKind =
          new HashMap<>();

      for (EmittedSpans.SpansByScope spansByScopeEntry : spans.getSpansByScope()) {
        String scope = spansByScopeEntry.getScope();

        attributesByScopeAndSpanKind.putIfAbsent(scope, new HashMap<>());
        Map<String, Set<TelemetryAttribute>> attributesBySpanKind =
            attributesByScopeAndSpanKind.get(scope);

        for (EmittedSpans.Span span : spansByScopeEntry.getSpans()) {
          String spanKind = span.getSpanKind();

          attributesBySpanKind.putIfAbsent(spanKind, new HashSet<>());
          Set<TelemetryAttribute> attributeSet = attributesBySpanKind.get(spanKind);

          if (span.getAttributes() != null) {
            for (TelemetryAttribute attr : span.getAttributes()) {
              attributeSet.add(new TelemetryAttribute(attr.getName(), attr.getType()));
            }
          }
        }
      }

      EmittedSpans deduplicatedEmittedSpans = getEmittedSpans(attributesByScopeAndSpanKind, when);
      result.put(when, deduplicatedEmittedSpans);
    }

    return result;
  }

  /**
   * Takes in a map of attributes by scope and span kind, and returns an {@link EmittedSpans} object
   * with deduplicated spans.
   *
   * @param attributesByScopeAndSpanKind the map of attributes by scope and span kind
   * @param when the condition under which the telemetry is emitted
   * @return an {@link EmittedSpans} object with deduplicated spans
   */
  private static EmittedSpans getEmittedSpans(
      Map<String, Map<String, Set<TelemetryAttribute>>> attributesByScopeAndSpanKind, String when) {
    List<EmittedSpans.SpansByScope> deduplicatedSpansByScope = new ArrayList<>();

    for (Map.Entry<String, Map<String, Set<TelemetryAttribute>>> scopeEntry :
        attributesByScopeAndSpanKind.entrySet()) {
      String scope = scopeEntry.getKey();
      Map<String, Set<TelemetryAttribute>> spanKindMap = scopeEntry.getValue();
      EmittedSpans.SpansByScope deduplicatedScope = getSpansByScope(scope, spanKindMap);
      deduplicatedSpansByScope.add(deduplicatedScope);
    }

    return new EmittedSpans(when, deduplicatedSpansByScope);
  }

  /**
   * Converts a map of attributes by spanKind into an {@link EmittedSpans.SpansByScope} object.
   * Deduplicates spans by their kind and collects their attributes.
   *
   * @param scope the name of the scope
   * @param spanKindMap a map where the key is the span kind and the value is set of attributes
   * @return an {@link EmittedSpans.SpansByScope} object with deduplicated spans
   */
  private static EmittedSpans.SpansByScope getSpansByScope(
      String scope, Map<String, Set<TelemetryAttribute>> spanKindMap) {

    List<EmittedSpans.Span> deduplicatedSpans = new ArrayList<>();

    for (Map.Entry<String, Set<TelemetryAttribute>> spanKindEntry : spanKindMap.entrySet()) {
      String spanKind = spanKindEntry.getKey();
      Set<TelemetryAttribute> attributes = spanKindEntry.getValue();

      List<TelemetryAttribute> attributeList = new ArrayList<>(attributes);
      EmittedSpans.Span deduplicatedSpan = new EmittedSpans.Span(spanKind, attributeList);
      deduplicatedSpans.add(deduplicatedSpan);
    }

    return new EmittedSpans.SpansByScope(scope, deduplicatedSpans);
  }

  private EmittedSpanParser() {}
}
