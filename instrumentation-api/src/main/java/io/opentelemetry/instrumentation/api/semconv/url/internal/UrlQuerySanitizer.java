/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.semconv.url.internal;

import java.util.Set;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class UrlQuerySanitizer {

  private static final String REDACTED = "REDACTED";

  private UrlQuerySanitizer() {}

  @Nullable
  public static String redactQueryString(@Nullable String query, Set<String> paramsToRedact) {
    if (query == null || query.isEmpty() || paramsToRedact.isEmpty()) {
      return query;
    }

    if (!containsParamToRedact(query, 0, query.length(), paramsToRedact)) {
      return query;
    }

    StringBuilder result = new StringBuilder();
    redactInternal(query, 0, paramsToRedact, result);
    return result.toString();
  }

  public static String redactUrl(String url, Set<String> paramsToRedact) {
    if (paramsToRedact.isEmpty()) {
      return url;
    }

    int questionMarkIndex = url.indexOf('?');
    if (questionMarkIndex == -1) {
      return url;
    }

    int queryStart = questionMarkIndex + 1;
    if (queryStart >= url.length()) {
      return url;
    }

    // Quick check to avoid allocation if no sensitive params are present
    if (!containsParamToRedact(url, queryStart, url.length(), paramsToRedact)) {
      return url;
    }

    StringBuilder result = new StringBuilder(url.length());
    result.append(url, 0, queryStart);
    redactInternal(url, queryStart, paramsToRedact, result);
    return result.toString();
  }

  // Core redaction logic: parses query parameters character by character and redacts values for
  // parameters in the paramsToRedact set. For non-sensitive parameters, value characters are also
  // appended to currentParamName, but this is harmless since we reset it at the next '&'.
  private static void redactInternal(
      String str, int startIndex, Set<String> paramsToRedact, StringBuilder result) {
    StringBuilder currentParamName = new StringBuilder();

    for (int i = startIndex; i < str.length(); i++) {
      char currentChar = str.charAt(i);

      if (currentChar == '=') {
        result.append('=');
        if (paramsToRedact.contains(currentParamName.toString())) {
          result.append(REDACTED);
          // Skip over parameter value until we hit '&', '#', or end of string
          for (; i + 1 < str.length(); i++) {
            char c = str.charAt(i + 1);
            if (c == '&' || c == '#') {
              break;
            }
          }
        }
      } else if (currentChar == '&') {
        result.append(currentChar);
        currentParamName.setLength(0);
      } else if (currentChar == '#') {
        result.append(str, i, str.length());
        break;
      } else {
        currentParamName.append(currentChar);
        result.append(currentChar);
      }
    }
  }

  // Quick check to avoid StringBuilder allocation when no redaction is needed.
  // False positives are acceptable since this is only an optimization - if we return true for a
  // substring match (e.g., "pass" matching "my_password=123"), the subsequent redactInternal call
  // will correctly parse parameter boundaries and only redact exact parameter name matches.
  private static boolean containsParamToRedact(
      String str, int startIndex, int endIndex, Set<String> paramsToRedact) {
    for (String param : paramsToRedact) {
      int index = str.indexOf(param, startIndex);
      if (index >= startIndex && index < endIndex) {
        return true;
      }
    }
    return false;
  }
}
