/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.internal.HttpProtocolUtil;
import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.pekko.http.javadsl.model.HttpResponse;

/**
 * Pekko HTTP 1.x does not expose the rejected request to the parsing error handler, so request
 * attributes are unavailable there. Pekko HTTP 2.0 exposes the method, the raw request-target and
 * the protocol, as far as they were read before the request was rejected. Headers are never
 * available.
 */
class PekkoHttpParsingErrorAttributesGetter
    implements HttpServerAttributesGetter<PekkoHttpParsingErrorRequest, HttpResponse> {

  @Nullable
  @Override
  public String getHttpRequestMethod(PekkoHttpParsingErrorRequest request) {
    return request.method();
  }

  @Override
  public List<String> getHttpRequestHeader(PekkoHttpParsingErrorRequest request, String name) {
    return emptyList();
  }

  @Override
  public Integer getHttpResponseStatusCode(
      PekkoHttpParsingErrorRequest request, HttpResponse response, @Nullable Throwable error) {
    return response.status().intValue();
  }

  @Override
  public List<String> getHttpResponseHeader(
      PekkoHttpParsingErrorRequest request, HttpResponse response, String name) {
    return response
        .getHeader(name)
        .map(header -> singletonList(header.value()))
        .orElse(emptyList());
  }

  @Nullable
  @Override
  public String getUrlScheme(PekkoHttpParsingErrorRequest request) {
    String target = withoutFragment(request.rawRequestTarget());
    if (target == null) {
      return null;
    }
    int schemeEnd = findSchemeEnd(target);
    if (schemeEnd < 0 || !target.startsWith("://", schemeEnd)) {
      return null;
    }
    // The scheme also labels the request duration metric, so only report known values.
    if (schemeEnd == 4 && target.regionMatches(true, 0, "http", 0, 4)) {
      return "http";
    }
    if (schemeEnd == 5 && target.regionMatches(true, 0, "https", 0, 5)) {
      return "https";
    }
    return null;
  }

  @Nullable
  @Override
  public String getUrlPath(PekkoHttpParsingErrorRequest request) {
    String target = pathAndQuery(request);
    if (target == null) {
      return null;
    }
    int queryStart = target.indexOf('?');
    String path = queryStart >= 0 ? target.substring(0, queryStart) : target;
    // an absolute-form target may have a query but no path
    return path.isEmpty() ? null : path;
  }

  @Nullable
  @Override
  public String getUrlQuery(PekkoHttpParsingErrorRequest request) {
    String target = pathAndQuery(request);
    if (target == null) {
      return null;
    }
    int queryStart = target.indexOf('?');
    return queryStart >= 0 ? target.substring(queryStart + 1) : null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(
      PekkoHttpParsingErrorRequest request, @Nullable HttpResponse response) {
    String protocol = request.protocol();
    return protocol != null ? HttpProtocolUtil.getProtocol(protocol) : null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      PekkoHttpParsingErrorRequest request, @Nullable HttpResponse response) {
    String protocol = request.protocol();
    return protocol != null ? HttpProtocolUtil.getVersion(protocol) : null;
  }

  /**
   * Returns the path and query of a request-target in origin form ({@code /path?query}) or absolute
   * form with or without an authority. Returns {@code null} for a CONNECT request, the asterisk
   * form of a server-wide OPTIONS request, anything else that is neither origin nor absolute form,
   * and a target that contains control characters.
   */
  @Nullable
  private static String pathAndQuery(PekkoHttpParsingErrorRequest request) {
    // a CONNECT target is a host and port, not a path, even when pekko-http renders an IPv6 one
    // with a leading // such as //[::1]:443
    if ("CONNECT".equals(request.method())) {
      return null;
    }
    String target = withoutFragment(request.rawRequestTarget());
    // a rejected target keeps any literal control characters, which exporters such as the logging
    // one write unescaped
    if (target == null || containsControlCharacter(target)) {
      return null;
    }
    if (target.startsWith("/")) {
      // pekko-http renders an authority-form target, userinfo included, as a single segment with a
      // leading //, for example when it cannot resolve the effective URI of an HTTP/1.0 request
      // without a Host header. It only renders a target of a request line that it parsed up to the
      // protocol, without a protocol the target is what the client sent and // starts its path.
      if (request.protocol() != null
          && target.startsWith("//")
          && target.indexOf('/', 2) < 0
          && target.indexOf('?') < 0) {
        return null;
      }
      return target;
    }
    int schemeEnd = findSchemeEnd(target);
    if (schemeEnd < 0) {
      return null;
    }
    int pathStart = schemeEnd + 1;
    if (!target.startsWith("//", pathStart)) {
      return target.substring(pathStart);
    }

    // the authority ends at the first '/' or '?', a '/' after a '?' belongs to the query
    pathStart += 2;
    while (pathStart < target.length() && "/?".indexOf(target.charAt(pathStart)) < 0) {
      pathStart++;
    }
    return pathStart < target.length() ? target.substring(pathStart) : null;
  }

  /**
   * A request-target never carries a fragment, but a rejected one may hold anything. Whatever
   * follows a {@code #} is dropped, query redaction does not look past it.
   */
  @Nullable
  private static String withoutFragment(@Nullable String target) {
    if (target == null) {
      return null;
    }
    int fragmentStart = target.indexOf('#');
    return fragmentStart >= 0 ? target.substring(0, fragmentStart) : target;
  }

  private static boolean containsControlCharacter(String target) {
    for (int i = 0; i < target.length(); i++) {
      if (Character.isISOControl(target.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  /** Returns the index of the colon after a valid URI scheme, or {@code -1} if absent. */
  private static int findSchemeEnd(String target) {
    if (target.isEmpty() || !isAsciiLetter(target.charAt(0))) {
      return -1;
    }
    for (int i = 1; i < target.length(); i++) {
      char c = target.charAt(i);
      if (c == ':') {
        return i;
      }
      if (!isAsciiLetter(c) && !(c >= '0' && c <= '9') && c != '+' && c != '-' && c != '.') {
        return -1;
      }
    }
    return -1;
  }

  private static boolean isAsciiLetter(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }
}
