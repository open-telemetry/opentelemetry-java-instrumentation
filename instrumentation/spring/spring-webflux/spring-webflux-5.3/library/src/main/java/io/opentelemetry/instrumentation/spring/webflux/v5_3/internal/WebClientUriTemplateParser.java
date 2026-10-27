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
    if (length == 0 || !hasBalancedExpressions(template)) {
      return null;
    }

    int authorityStart;
    if (template.charAt(0) == '/') {
      if (length == 1 || template.charAt(1) != '/') {
        return template;
      }
      authorityStart = 2;
    } else {
      int schemeDelimiter = findSchemeDelimiter(template);
      if (schemeDelimiter == -1) {
        return null;
      }
      authorityStart = schemeDelimiter + 3;
    }

    if (authorityStart >= length) {
      return null;
    }

    int pathStart = findAuthorityEnd(template, authorityStart);
    if (pathStart == -1
        || template.charAt(pathStart) != '/'
        || pathStart == authorityStart
        || (pathStart + 1 < length && template.charAt(pathStart + 1) == '/')) {
      return null;
    }
    return template.substring(pathStart);
  }

  private static boolean hasBalancedExpressions(String template) {
    for (int i = 0; i < template.length(); i++) {
      char c = template.charAt(i);
      if (c == '}') {
        return false;
      }
      if (c == '{') {
        int end = findExpressionEnd(template, i);
        if (end == -1) {
          return false;
        }
        i = end - 1;
      }
    }
    return true;
  }

  private static int findSchemeDelimiter(String template) {
    for (int i = 0; i < template.length(); i++) {
      char c = template.charAt(i);
      if (c == '{') {
        i = findExpressionEnd(template, i) - 1;
      } else if (c == '/' || c == '?' || c == '#') {
        return -1;
      } else if (c == ':'
          && i + 2 < template.length()
          && template.charAt(i + 1) == '/'
          && template.charAt(i + 2) == '/'
          && isValidScheme(template, i)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isValidScheme(String template, int end) {
    if (end == 0 || !(isAsciiLetter(template.charAt(0)) || template.charAt(0) == '{')) {
      return false;
    }
    for (int i = 0; i < end; i++) {
      char c = template.charAt(i);
      if (c == '{') {
        int expressionEnd = findExpressionEnd(template, i);
        if (expressionEnd == -1 || expressionEnd > end) {
          return false;
        }
        i = expressionEnd - 1;
      } else if (!(isAsciiLetter(c)
          || (c >= '0' && c <= '9')
          || c == '+'
          || c == '-'
          || c == '.')) {
        return false;
      }
    }
    return true;
  }

  private static int findAuthorityEnd(String template, int start) {
    for (int i = start; i < template.length(); i++) {
      char c = template.charAt(i);
      if (c == '{') {
        i = findExpressionEnd(template, i) - 1;
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
          return i + 1;
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
