/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import java.net.InetSocketAddress;
import javax.annotation.Nullable;
import org.apache.pekko.http.scaladsl.model.HttpMethod;
import org.apache.pekko.http.scaladsl.model.Uri;
import org.apache.pekko.util.ByteString;

/**
 * What the parser had read of the request line, held as the values pekko-http itself keeps.
 *
 * <p>This is recorded for every request, the ones that parse included, so it does nothing beyond
 * holding on to references that already exist. Rendering the target and scanning it for characters
 * that have to be encoded happens in {@link #toParsingError}, which only runs for a request that
 * turned out to fail.
 */
final class PekkoHttpRequestLine {

  @Nullable private final HttpMethod method;
  @Nullable private final Uri uri;
  @Nullable private final ByteString rawTarget;

  private PekkoHttpRequestLine(
      @Nullable HttpMethod method, @Nullable Uri uri, @Nullable ByteString rawTarget) {
    this.method = method;
    this.uri = uri;
    this.rawTarget = rawTarget;
  }

  /**
   * The request line parsed, which is the common case because most parsing failures happen in the
   * headers. The target is the one pekko-http itself validated.
   */
  static PekkoHttpRequestLine parsed(@Nullable HttpMethod method, Uri uri) {
    return new PekkoHttpRequestLine(method, uri, null);
  }

  /**
   * The request target is what failed to parse, so it is only available as the bytes that arrived.
   * They are bounded by the {@code max-uri-length} parser setting, but are otherwise unvalidated.
   */
  static PekkoHttpRequestLine unparsed(
      @Nullable HttpMethod method, @Nullable ByteString rawTarget) {
    return new PekkoHttpRequestLine(method, null, rawTarget);
  }

  /** Builds what the span reports from what the parser read. */
  PekkoHttpParsingError toParsingError(@Nullable InetSocketAddress peerAddress) {
    String methodValue = method == null ? null : method.value();
    if (uri != null) {
      return PekkoHttpParsingError.create(
          methodValue,
          uri.path().toString(),
          uri.rawQueryString().isDefined() ? uri.rawQueryString().get() : null,
          peerAddress);
    }
    if (rawTarget == null) {
      return PekkoHttpParsingError.create(methodValue, null, null, peerAddress);
    }
    String target = rawTarget.utf8String();
    // only an origin form target carries a path that can be reported as one; the target of a
    // request that failed to parse may just as well be an absolute form, an authority form, or too
    // broken to tell the two apart, and none of those can be cut into a path safely, so the target
    // is left out rather than reported as a path it is not
    if (!target.startsWith("/")) {
      return PekkoHttpParsingError.create(methodValue, null, null, peerAddress);
    }
    int query = target.indexOf('?');
    return PekkoHttpParsingError.create(
        methodValue,
        query < 0 ? target : target.substring(0, query),
        query < 0 ? null : target.substring(query + 1),
        peerAddress);
  }
}
