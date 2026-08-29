/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * Describes a request that pekko-http rejected while parsing it. Such a request never becomes an
 * {@code HttpRequest}, so it is described by whatever the parser had read before it gave up, which
 * may be nothing at all.
 */
final class PekkoHttpParsingError {

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  @Nullable private final String method;
  @Nullable private final String path;
  @Nullable private final String query;
  @Nullable private final InetSocketAddress peerAddress;

  private PekkoHttpParsingError(
      @Nullable String method,
      @Nullable String path,
      @Nullable String query,
      @Nullable InetSocketAddress peerAddress) {
    this.method = method;
    this.path = escapeControlCharacters(path);
    this.query = escapeControlCharacters(query);
    this.peerAddress = peerAddress;
  }

  static PekkoHttpParsingError create(
      @Nullable String method,
      @Nullable String path,
      @Nullable String query,
      @Nullable InetSocketAddress peerAddress) {
    return new PekkoHttpParsingError(method, path, query, peerAddress);
  }

  /**
   * Describes a request that was rejected before anything of its request line was read, or one
   * rejected by a stage that no longer has the parser in reach.
   */
  static PekkoHttpParsingError unknown(@Nullable InetSocketAddress peerAddress) {
    return new PekkoHttpParsingError(null, null, null, peerAddress);
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

  @Nullable
  InetSocketAddress peerAddress() {
    return peerAddress;
  }
}
