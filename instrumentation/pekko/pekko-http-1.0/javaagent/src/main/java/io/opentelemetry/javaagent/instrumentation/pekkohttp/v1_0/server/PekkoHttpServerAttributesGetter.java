/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server;

import static io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.server.PekkoHttpServerSingletons.HTTP_REQUEST_PEER_ADDRESS;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0.PekkoHttpUtil;
import java.net.InetSocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.pekko.http.scaladsl.model.HttpRequest;
import org.apache.pekko.http.scaladsl.model.HttpResponse;
import org.apache.pekko.http.scaladsl.model.Uri;
import scala.Option;

class PekkoHttpServerAttributesGetter
    implements HttpServerAttributesGetter<HttpRequest, HttpResponse> {

  private static final String AUTHORITY_PSEUDO_HEADER = ":authority";

  @Override
  public String getHttpRequestMethod(HttpRequest request) {
    return request.method().value();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpRequest request, String name) {
    // http/2 requests don't have a host header, pekko puts the value of the :authority pseudo
    // header into the request uri
    if (AUTHORITY_PSEUDO_HEADER.equals(name)) {
      return authority(request);
    }
    return PekkoHttpUtil.requestHeader(request, name);
  }

  private static List<String> authority(HttpRequest request) {
    Uri.Authority authority = request.uri().authority();
    Uri.Host host = authority.host();
    if (host.isEmpty()) {
      return emptyList();
    }
    int port = authority.port();
    return singletonList(port > 0 ? host + ":" + port : host.toString());
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpRequest request, HttpResponse httpResponse, @Nullable Throwable error) {
    return httpResponse.status().intValue();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpRequest request, HttpResponse httpResponse, String name) {
    return PekkoHttpUtil.responseHeader(httpResponse, name);
  }

  @Override
  public String getUrlScheme(HttpRequest request) {
    return request.uri().scheme();
  }

  @Override
  public String getUrlPath(HttpRequest request) {
    return request.uri().path().toString();
  }

  @Nullable
  @Override
  public String getUrlQuery(HttpRequest request) {
    Option<String> queryString = request.uri().rawQueryString();
    return queryString.isDefined() ? queryString.get() : null;
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(HttpRequest request, @Nullable HttpResponse httpResponse) {
    return PekkoHttpUtil.protocolName(request);
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      HttpRequest request, @Nullable HttpResponse httpResponse) {
    return PekkoHttpUtil.protocolVersion(request);
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      HttpRequest request, @Nullable HttpResponse httpResponse) {
    return HTTP_REQUEST_PEER_ADDRESS.get(request);
  }
}
