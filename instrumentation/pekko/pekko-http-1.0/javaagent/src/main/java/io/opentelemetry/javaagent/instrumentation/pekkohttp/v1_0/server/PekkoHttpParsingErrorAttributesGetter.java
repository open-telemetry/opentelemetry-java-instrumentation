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
    String target = request.rawRequestTarget();
    if (target == null || target.startsWith("/")) {
      return null;
    }
    int schemeEnd = target.indexOf("://");
    return schemeEnd > 0 ? target.substring(0, schemeEnd) : null;
  }

  @Nullable
  @Override
  public String getUrlPath(PekkoHttpParsingErrorRequest request) {
    String target = pathAndQuery(request.rawRequestTarget());
    if (target == null) {
      return null;
    }
    int queryStart = target.indexOf('?');
    return queryStart >= 0 ? target.substring(0, queryStart) : target;
  }

  @Nullable
  @Override
  public String getUrlQuery(PekkoHttpParsingErrorRequest request) {
    String target = pathAndQuery(request.rawRequestTarget());
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
   * form ({@code http://host/path?query}), and {@code null} for the authority form of a CONNECT
   * request and the asterisk form of a server-wide OPTIONS request, which have neither.
   */
  @Nullable
  private static String pathAndQuery(@Nullable String target) {
    if (target == null || target.startsWith("/")) {
      return target;
    }
    int schemeEnd = target.indexOf("://");
    if (schemeEnd <= 0) {
      return null;
    }
    int pathStart = target.indexOf('/', schemeEnd + 3);
    return pathStart >= 0 ? target.substring(pathStart) : null;
  }
}
