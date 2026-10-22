/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.ByteBuffer;
import javax.annotation.Nullable;

/**
 * Extracts a prefix from an already-decoded Redis server-error line. Callers must establish
 * server-error provenance and handle RESP framing; this utility does not parse raw responses or
 * client or aggregate exception messages.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class RedisErrorPrefix {

  /**
   * Returns the prefix from {@link #extract(String)} only when every character in the prefix is
   * ASCII, or {@code null} otherwise.
   */
  @Nullable
  public static String extractAscii(@Nullable String serverErrorLine) {
    String prefix = extract(serverErrorLine);
    if (prefix == null) {
      return null;
    }
    for (int i = 0; i < prefix.length(); i++) {
      if (prefix.charAt(i) > 0x7f) {
        return null;
      }
    }
    return prefix;
  }

  /**
   * Returns the first nonempty ASCII-space-delimited token unchanged, or {@code null} for null,
   * empty, or ASCII-space-only input. Only ASCII space U+0020 separates tokens; case, punctuation,
   * tabs, other characters, and token length are unrestricted.
   */
  @Nullable
  public static String extract(@Nullable String serverErrorLine) {
    if (serverErrorLine == null) {
      return null;
    }
    int start = 0;
    while (start < serverErrorLine.length() && serverErrorLine.charAt(start) == ' ') {
      start++;
    }
    if (start == serverErrorLine.length()) {
      return null;
    }
    int end = serverErrorLine.indexOf(' ', start);
    return end < 0 ? serverErrorLine.substring(start) : serverErrorLine.substring(start, end);
  }

  /** Returns the first token from a bounded UTF-8 server error line, or {@code null} if absent. */
  @Nullable
  public static String extract(@Nullable ByteBuffer errorLine) {
    if (errorLine == null) {
      return null;
    }

    int start = errorLine.position();
    int limit = errorLine.limit();
    while (start < limit && errorLine.get(start) == ' ') {
      start++;
    }
    if (start == limit) {
      return null;
    }

    int end = start;
    while (end < limit && errorLine.get(end) != ' ') {
      end++;
    }

    ByteBuffer token = errorLine.asReadOnlyBuffer();
    token.position(start);
    token.limit(end);
    return UTF_8.decode(token).toString();
  }

  private RedisErrorPrefix() {}
}
