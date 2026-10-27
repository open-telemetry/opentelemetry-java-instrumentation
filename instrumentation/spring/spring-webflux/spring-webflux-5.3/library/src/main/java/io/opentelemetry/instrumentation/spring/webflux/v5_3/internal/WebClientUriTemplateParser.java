/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3.internal;

import javax.annotation.Nullable;

final class WebClientUriTemplateParser {

  @Nullable
  static String extractPathTemplate(String template) {
    int length = template.length();
    if (length == 0 || !isValidParameterizedTemplate(template)) {
      return null;
    }

    if (template.startsWith("/") && !template.startsWith("//")) {
      return template;
    }

    int authorityStart = findAuthorityStart(template);
    if (authorityStart == -1 || authorityStart >= length) {
      return null;
    }

    int pathStart = findAuthorityEnd(template, authorityStart);
    if (pathStart == -1
        || template.charAt(pathStart) != '/'
        || pathStart == authorityStart
        || template.startsWith("//", pathStart)) {
      return null;
    }
    return template.substring(pathStart);
  }

  private static boolean isValidParameterizedTemplate(String template) {
    boolean hasUriVariable = false;
    for (int i = 0; i < template.length(); i++) {
      char c = template.charAt(i);
      if (c == '}') {
        return false;
      }
      if (c == '{') {
        int end = findExpressionEnd(template, i);
        if (end == -1 || !isUriVariable(template, i, end)) {
          return false;
        }
        hasUriVariable = true;
        i = end;
      }
    }
    return hasUriVariable;
  }

  private static boolean isUriVariable(String template, int start, int end) {
    int nameStart = start + 1;
    int nameEnd = nameStart;
    while (nameEnd < end && isUriVariableNameCharacter(template.charAt(nameEnd))) {
      nameEnd++;
    }
    if (nameEnd == nameStart) {
      return false;
    }
    if (nameEnd == end) {
      return true;
    }
    return template.charAt(nameEnd) == ':' && nameEnd + 1 < end;
  }

  private static boolean isUriVariableNameCharacter(char c) {
    return isAsciiLetter(c) || (c >= '0' && c <= '9') || c == '_' || c == '.' || c == '-';
  }

  private static int findAuthorityStart(String template) {
    if (template.startsWith("//")) {
      return 2;
    }
    if (!(isAsciiLetter(template.charAt(0)) || template.charAt(0) == '{')) {
      return -1;
    }
    for (int i = 0; i < template.length(); i++) {
      char c = template.charAt(i);
      if (c == '{') {
        i = findExpressionEnd(template, i);
      } else if (c == ':') {
        return template.startsWith("://", i) ? i + 3 : -1;
      } else if (!(isAsciiLetter(c)
          || (c >= '0' && c <= '9')
          || c == '+'
          || c == '-'
          || c == '.')) {
        return -1;
      }
    }
    return -1;
  }

  private static int findAuthorityEnd(String template, int start) {
    for (int i = start; i < template.length(); i++) {
      char c = template.charAt(i);
      if (c == '{') {
        i = findExpressionEnd(template, i);
      } else if (c == '/' || c == '?' || c == '#') {
        return i;
      }
    }
    return -1;
  }

  private static int findExpressionEnd(String template, int start) {
    int depth = 0;
    for (int i = start; i < template.length(); i++) {
      char c = template.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        if (template.charAt(i - 1) == '{') {
          return -1;
        }
        if (--depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static boolean isAsciiLetter(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private WebClientUriTemplateParser() {}
}
