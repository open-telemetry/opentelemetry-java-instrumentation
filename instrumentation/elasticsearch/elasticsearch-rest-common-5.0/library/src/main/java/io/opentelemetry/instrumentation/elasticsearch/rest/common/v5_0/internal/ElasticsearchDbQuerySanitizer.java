/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.elasticsearch.rest.common.v5_0.internal;

import javax.annotation.Nullable;

/**
 * Rewrites an Elasticsearch search request body so that every literal value is replaced with the
 * mask {@code "?"} while the structure and field names are preserved. For example {@code
 * {"query":{"match":{"title":"secret user data"}}}} becomes {@code
 * {"query":{"match":{"title":"?"}}}}.
 *
 * <p>This is a small dependency-free JSON scanner. It is deliberately not backed by Jackson: the
 * Elasticsearch low-level REST client does not put Jackson on the application classpath, and a
 * Jackson-based helper in the javaagent module would make muzzle disable the whole instrumentation
 * on applications without Jackson. It masks the same set of values as the OpenSearch
 * instrumentation (which uses {@code SanitizingJacksonJsonGenerator}): every string, number,
 * boolean and null in value position is replaced with the JSON string {@code "?"}, while object
 * keys and structure are kept.
 *
 * <p>When the body is not valid JSON the scanner returns {@code null} so that the caller drops the
 * body rather than capturing it raw.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
final class ElasticsearchDbQuerySanitizer {

  private static final String MASKED_VALUE = "\"?\"";

  // Bounds the recursion depth of the descent below so that a deeply nested but otherwise valid
  // body cannot overflow the stack. This code runs on the application's own thread, already partway
  // down a client-plus-instrumentation call stack that may have a small stack size, and a
  // StackOverflowError would escape the RuntimeException handler in sanitize(). Real Elasticsearch
  // query bodies nest at most a few dozen levels, so this sits well above any realistic query.
  static final int MAX_NESTING_DEPTH = 200;

  private final String json;
  private int pos;
  private int depth;

  private ElasticsearchDbQuerySanitizer(String json) {
    this.json = json;
  }

  @Nullable
  static String sanitize(String body) {
    ElasticsearchDbQuerySanitizer scanner = new ElasticsearchDbQuerySanitizer(body);
    StringBuilder out = new StringBuilder(body.length());
    try {
      scanner.skipWhitespace();
      scanner.maskValue(out);
      scanner.skipWhitespace();
      if (!scanner.atEnd()) {
        // trailing content after a complete JSON value means the body is not valid JSON
        return null;
      }
    } catch (RuntimeException ignored) {
      // the body could not be sanitized: it is not valid JSON, or it is nested more deeply than
      // MAX_NESTING_DEPTH. Either way it must not be captured raw, so drop it instead.
      return null;
    }
    return out.length() == 0 ? null : out.toString();
  }

  private void maskValue(StringBuilder out) {
    char c = current();
    switch (c) {
      case '{':
        maskObject(out);
        break;
      case '[':
        maskArray(out);
        break;
      case '"':
        consumeString();
        out.append(MASKED_VALUE);
        break;
      case 't':
        expect("true");
        out.append(MASKED_VALUE);
        break;
      case 'f':
        expect("false");
        out.append(MASKED_VALUE);
        break;
      case 'n':
        expect("null");
        out.append(MASKED_VALUE);
        break;
      default:
        if (c == '-' || (c >= '0' && c <= '9')) {
          consumeNumber();
          out.append(MASKED_VALUE);
        } else {
          throw new MalformedJsonException();
        }
        break;
    }
  }

  private void maskObject(StringBuilder out) {
    enterNesting();
    pos++; // consume '{'
    out.append('{');
    skipWhitespace();
    if (current() == '}') {
      pos++;
      out.append('}');
      depth--;
      return;
    }
    while (true) {
      skipWhitespace();
      if (current() != '"') {
        throw new MalformedJsonException();
      }
      out.append(consumeString()); // object keys are preserved verbatim
      skipWhitespace();
      if (current() != ':') {
        throw new MalformedJsonException();
      }
      pos++;
      out.append(':');
      skipWhitespace();
      maskValue(out);
      skipWhitespace();
      char c = current();
      if (c == ',') {
        pos++;
        out.append(',');
      } else if (c == '}') {
        pos++;
        out.append('}');
        depth--;
        return;
      } else {
        throw new MalformedJsonException();
      }
    }
  }

  private void maskArray(StringBuilder out) {
    enterNesting();
    pos++; // consume '['
    out.append('[');
    skipWhitespace();
    if (current() == ']') {
      pos++;
      out.append(']');
      depth--;
      return;
    }
    while (true) {
      skipWhitespace();
      maskValue(out);
      skipWhitespace();
      char c = current();
      if (c == ',') {
        pos++;
        out.append(',');
      } else if (c == ']') {
        pos++;
        out.append(']');
        depth--;
        return;
      } else {
        throw new MalformedJsonException();
      }
    }
  }

  private void enterNesting() {
    if (++depth > MAX_NESTING_DEPTH) {
      throw new NestingTooDeepException();
    }
  }

  /** Consumes a JSON string starting at the current position and returns it verbatim. */
  private String consumeString() {
    int start = pos;
    pos++; // consume opening quote
    while (true) {
      char c = next();
      if (c == '\\') {
        char escaped = next();
        if (escaped == 'u') {
          for (int i = 0; i < 4; i++) {
            char hex = next();
            boolean isHex =
                (hex >= '0' && hex <= '9')
                    || (hex >= 'a' && hex <= 'f')
                    || (hex >= 'A' && hex <= 'F');
            if (!isHex) {
              throw new MalformedJsonException();
            }
          }
        } else if ("\"\\/bfnrt".indexOf(escaped) < 0) {
          throw new MalformedJsonException();
        }
      } else if (c == '"') {
        return json.substring(start, pos);
      } else if (c < 0x20) {
        // unescaped control characters are not allowed in JSON strings
        throw new MalformedJsonException();
      }
    }
  }

  private void consumeNumber() {
    if (current() == '-') {
      pos++;
    }
    char c = current();
    if (c == '0') {
      pos++;
    } else if (c >= '1' && c <= '9') {
      consumeDigits();
    } else {
      throw new MalformedJsonException();
    }
    if (!atEnd() && json.charAt(pos) == '.') {
      pos++;
      consumeDigits();
    }
    if (!atEnd() && (json.charAt(pos) == 'e' || json.charAt(pos) == 'E')) {
      pos++;
      if (!atEnd() && (json.charAt(pos) == '+' || json.charAt(pos) == '-')) {
        pos++;
      }
      consumeDigits();
    }
  }

  private void consumeDigits() {
    int start = pos;
    while (!atEnd() && json.charAt(pos) >= '0' && json.charAt(pos) <= '9') {
      pos++;
    }
    if (pos == start) {
      throw new MalformedJsonException();
    }
  }

  private void expect(String literal) {
    if (!json.regionMatches(pos, literal, 0, literal.length())) {
      throw new MalformedJsonException();
    }
    pos += literal.length();
  }

  private void skipWhitespace() {
    while (!atEnd()) {
      char c = json.charAt(pos);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        pos++;
      } else {
        return;
      }
    }
  }

  private char current() {
    if (atEnd()) {
      throw new MalformedJsonException();
    }
    return json.charAt(pos);
  }

  private char next() {
    if (atEnd()) {
      throw new MalformedJsonException();
    }
    return json.charAt(pos++);
  }

  private boolean atEnd() {
    return pos >= json.length();
  }

  private static class MalformedJsonException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    MalformedJsonException() {
      super(null, null, false, false);
    }
  }

  private static class NestingTooDeepException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    NestingTooDeepException() {
      super(null, null, false, false);
    }
  }
}
