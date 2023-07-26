/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClientResponse;

final class ReactorNettyNetClientAttributesGetter
    implements NetClientAttributesGetter<HttpClientRequest, HttpClientResponse> {

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
  public InetSocketAddress getServerInetSocketAddress(
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
