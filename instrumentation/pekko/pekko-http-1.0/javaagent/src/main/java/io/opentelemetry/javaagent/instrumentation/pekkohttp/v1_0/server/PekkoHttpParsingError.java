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

  static final PekkoHttpParsingError UNKNOWN = new PekkoHttpParsingError(null, null, null);

  @Nullable private final String method;
  @Nullable private final String path;
  @Nullable private final String query;

  private PekkoHttpParsingError(
      @Nullable String method, @Nullable String path, @Nullable String query) {
    this.method = method;
    this.path = path;
    this.query = query;
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
