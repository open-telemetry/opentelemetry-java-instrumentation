/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.httpserver.v4_0;

import static java.util.Collections.singletonList;

import io.opentelemetry.instrumentation.api.semconv.http.HttpServerAttributesGetter;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.SocketAddress;
import java.util.List;
import javax.annotation.Nullable;

final class VertxHttpServerAttributesGetter
    implements HttpServerAttributesGetter<HttpServerRequest, HttpServerResponse> {

  @Override
  public String getHttpRequestMethod(HttpServerRequest request) {
    return request.method().name();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpServerRequest request, String name) {
    List<String> values = request.headers().getAll(name);
    if (values.isEmpty() && name.equals(":authority")) {
      // Vert.x 4.0 removes the pseudo-header from headers(), but preserves it in host().
      String authority = request.host();
      if (authority != null) {
        return singletonList(authority);
      }
    }
    return values;
  }

  @Override
  public Iterable<String> getHttpRequestHeaderNames(HttpServerRequest request) {
    return request.headers().names();
  }

  @Nullable
  @Override
  public Integer getHttpResponseStatusCode(
      HttpServerRequest request, HttpServerResponse response, @Nullable Throwable error) {
    return response.headWritten() ? response.getStatusCode() : null;
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpServerRequest request, HttpServerResponse response, String name) {
    return response.headers().getAll(name);
  }

  @Override
  public Iterable<String> getHttpResponseHeaderNames(
      HttpServerRequest request, HttpServerResponse response) {
    return response.headers().names();
  }

  @Override
  public String getUrlScheme(HttpServerRequest request) {
    return request.scheme();
  }

  @Override
  public String getUrlPath(HttpServerRequest request) {
    return request.path();
  }

  @Nullable
  @Override
  public String getUrlQuery(HttpServerRequest request) {
    return request.query();
  }

  @Override
  public String getNetworkProtocolName(
      HttpServerRequest request, @Nullable HttpServerResponse response) {
    return "http";
  }

  @Override
  public String getNetworkProtocolVersion(
      HttpServerRequest request, @Nullable HttpServerResponse response) {
    return "2";
  }

  @Nullable
  @Override
  public String getNetworkPeerAddress(
      HttpServerRequest request, @Nullable HttpServerResponse response) {
    SocketAddress address = request.remoteAddress();
    return address == null ? null : address.hostAddress();
  }

  @Nullable
  @Override
  public Integer getNetworkPeerPort(
      HttpServerRequest request, @Nullable HttpServerResponse response) {
    SocketAddress address = request.remoteAddress();
    return address == null ? null : address.port();
  }
}
