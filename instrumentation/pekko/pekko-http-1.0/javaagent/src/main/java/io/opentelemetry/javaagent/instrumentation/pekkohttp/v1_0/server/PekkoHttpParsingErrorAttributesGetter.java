/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.pekko.http.javadsl.model.HttpResponse;

/**
 * The request failed to parse, so it is described by whatever the parser had read before it gave
 * up. Anything it never got to is reported as unknown.
 */
class PekkoHttpParsingErrorAttributesGetter
    implements HttpServerAttributesGetter<PekkoHttpParsingError, HttpResponse> {

  @Nullable
  @Override
  public String getHttpRequestMethod(PekkoHttpParsingError request) {
    return request.method();
  }

  @Override
  public List<String> getHttpRequestHeader(PekkoHttpParsingError request, String name) {
    return emptyList();
  }

  @Override
  public Integer getHttpResponseStatusCode(
      PekkoHttpParsingError request, HttpResponse response, @Nullable Throwable error) {
    return response.status().intValue();
  }

  @Override
  public List<String> getHttpResponseHeader(
      PekkoHttpParsingError request, HttpResponse response, String name) {
    return response
        .getHeader(name)
        .map(header -> singletonList(header.value()))
        .orElse(emptyList());
  }

  @Nullable
  @Override
  public String getUrlScheme(PekkoHttpParsingError request) {
    return null;
  }

  @Nullable
  @Override
  public String getUrlPath(PekkoHttpParsingError request) {
    return request.path();
  }

  @Nullable
  @Override
  public String getUrlQuery(PekkoHttpParsingError request) {
    return request.query();
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      PekkoHttpParsingError request, @Nullable HttpResponse response) {
    return request.peerAddress();
  }
}
