/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import javax.annotation.Nullable;
import org.apache.pekko.http.scaladsl.model.HttpMethod;
import org.apache.pekko.http.scaladsl.model.Uri;
import org.apache.pekko.util.ByteString;

/**
 * Describes a request that pekko-http rejected while parsing it. Such a request never becomes an
 * {@code HttpRequest}, so it is described by whatever the parser had read before it gave up, which
 * may be nothing at all.
 */
final class PekkoHttpParsingError {

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  static final PekkoHttpParsingError UNKNOWN = new PekkoHttpParsingError(null, null, null);

  @Nullable private final String method;
  @Nullable private final String path;
  @Nullable private final String query;

  private PekkoHttpParsingError(
      @Nullable String method, @Nullable String path, @Nullable String query) {
    this.method = method;
    this.path = escapeControlCharacters(path);
    this.query = escapeControlCharacters(query);
  }

  /**
   * Percent encodes control characters. A request target cannot contain a space, a tab, CR or LF,
   * because those end the token the parser reads it from, but every other control character can
   * reach here when the target is the thing that failed to parse, and {@code url.path} is not
   * sanitized on the way out the way {@code url.query} is. It is applied to a target that
   * pekko-http did parse as well, rather than depending on how {@code Uri.Path} renders one.
   *
   * <p>The C1 characters are encoded along with the C0 ones because the raw target is decoded as
   * utf-8, which turns a pair of bytes on the wire into one of them.
   */
  @Nullable
  private static String escapeControlCharacters(@Nullable String value) {
    if (value == null) {
      return null;
    }
    StringBuilder escaped = null;
    for (int i = 0; i < value.length(); i++) {
      char character = value.charAt(i);
      if (character > 0x1f && (character < 0x7f || character > 0x9f)) {
        if (escaped != null) {
          escaped.append(character);
        }
        continue;
      }
      if (escaped == null) {
        escaped = new StringBuilder(value.length() + 8).append(value, 0, i);
      }
      escaped.append('%').append(HEX[character >> 4]).append(HEX[character & 0xf]);
    }
    return escaped == null ? value : escaped.toString();
  }

  /**
   * Describes a request whose request line parsed, which is the common case because most parsing
   * failures happen in the headers. The target is the one pekko-http itself validated.
   */
  static PekkoHttpParsingError parsed(@Nullable HttpMethod method, Uri uri) {
    return new PekkoHttpParsingError(
        methodValue(method),
        uri.path().toString(),
        uri.rawQueryString().isDefined() ? uri.rawQueryString().get() : null);
  }

  /**
   * Describes a request whose request target is what failed to parse, so the target is kept exactly
   * as it arrived. It is bounded by the {@code max-uri-length} parser setting, but is otherwise
   * unvalidated.
   */
  static PekkoHttpParsingError unparsed(@Nullable HttpMethod method, @Nullable ByteString target) {
    if (target == null) {
      return new PekkoHttpParsingError(methodValue(method), null, null);
    }
    String rawTarget = target.utf8String();
    int query = rawTarget.indexOf('?');
    return new PekkoHttpParsingError(
        methodValue(method),
        query < 0 ? rawTarget : rawTarget.substring(0, query),
        query < 0 ? null : rawTarget.substring(query + 1));
  }

  @Nullable
  private static String methodValue(@Nullable HttpMethod method) {
    return method == null ? null : method.value();
  }

  @Nullable
  String method() {
    return method;
  }

  @Nullable
  String path() {
    return path;
  }

  @Nullable
  String query() {
    return query;
  }
}
