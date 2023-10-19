/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import javax.annotation.Nullable;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

final class ReactorNettyHttpClientAttributesGetter
    implements HttpClientAttributesGetter<HttpClientRequest, HttpClientResponse> {

  @Override
  public String getUrlFull(HttpClientRequest request) {
    return request.resourceUrl();
  }

  @Override
  public String getHttpRequestMethod(HttpClientRequest request) {
    return request.method().name();
  }

  @Override
  public List<String> getHttpRequestHeader(HttpClientRequest request, String name) {
    return request.requestHeaders().getAll(name);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      HttpClientRequest request, HttpClientResponse response, @Nullable Throwable error) {
    return response.status().code();
  }

  @Override
  public List<String> getHttpResponseHeader(
      HttpClientRequest request, HttpClientResponse response, String name) {
    return response.responseHeaders().getAll(name);
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(
      HttpClientRequest request, @Nullable HttpClientResponse response) {
    if (response == null) {
      return null;
    }
    return response.version().protocolName();
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      HttpClientRequest request, @Nullable HttpClientResponse response) {
    if (response == null) {
      return null;
    }
    HttpVersion version = response.version();
    if (version.minorVersion() == 0) {
      return Integer.toString(version.majorVersion());
    }
    return version.majorVersion() + "." + version.minorVersion();
  }

  @Nullable
  @Override
  public String getServerAddress(HttpClientRequest request) {
    return getHost(request);
  }

  @Nullable
  @Override
  public Integer getServerPort(HttpClientRequest request) {
    return getPort(request);
  }

  @Nullable
  @Override
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      HttpClientRequest request, @Nullable HttpClientResponse response) {

    // we're making use of the fact that HttpClientOperations is both a Connection and an
    // HttpClientResponse
    if (response instanceof Connection) {
      Connection connection = (Connection) response;
      SocketAddress address = connection.channel().remoteAddress();
      if (address instanceof InetSocketAddress) {
        return (InetSocketAddress) address;
      }
    }
    return null;
  }

  @Nullable
  private static String getHost(HttpClientRequest request) {
    String resourceUrl = request.resourceUrl();
    return resourceUrl == null ? null : UrlParser.getHost(resourceUrl);
  }

  @Nullable
  private static Integer getPort(HttpClientRequest request) {
    String resourceUrl = request.resourceUrl();
    return resourceUrl == null ? null : UrlParser.getPort(resourceUrl);
  }
}
