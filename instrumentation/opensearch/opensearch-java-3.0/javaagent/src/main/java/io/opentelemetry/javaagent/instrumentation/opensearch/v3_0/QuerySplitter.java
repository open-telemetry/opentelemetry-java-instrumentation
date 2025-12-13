/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Splits multiple queries separated by semicolons. Single Responsibility: Only responsible for
 * query separation logic.
 */
class QuerySplitter {

  private static final String QUERY_SEPARATOR = "\n";
  private static final String QUERY_COMBINATOR = ";";

  private QuerySplitter() {}

  /**
   * Splits a string containing multiple queries separated by semicolons.
   *
   * @param queriesString input string containing queries
   * @return list of individual query strings, empty if input is null or empty
   */
  static List<String> splitQueries(String queriesString) {
    if (queriesString == null || queriesString.trim().isEmpty()) {
      return Collections.emptyList();
    }

    String[] queries = queriesString.split(QUERY_SEPARATOR, -1);
    List<String> result = new ArrayList<>();

    for (String query : queries) {
      String trimmed = query.trim();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }

    return result;
  }

  /**
   * Joins multiple sanitized queries back into a single string.
   *
   * @param sanitizedQueries list of sanitized query strings
   * @return joined string with semicolon separator, or null if list is empty
   */
  static String joinQueries(List<String> sanitizedQueries) {
    if (sanitizedQueries == null || sanitizedQueries.isEmpty()) {
      return null;
    }

    return String.join(QUERY_COMBINATOR, sanitizedQueries);
  }
}
