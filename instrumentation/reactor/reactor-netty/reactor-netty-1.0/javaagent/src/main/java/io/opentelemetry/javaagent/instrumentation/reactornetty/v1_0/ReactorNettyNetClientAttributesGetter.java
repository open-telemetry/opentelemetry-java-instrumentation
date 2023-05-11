/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientConfig;
import reactor.netty.http.client.HttpClientResponse;

final class ReactorNettyNetClientAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<HttpClientConfig, HttpClientResponse> {

  @Nullable
  @Override
  public String getProtocolName(HttpClientConfig request, @Nullable HttpClientResponse response) {
    if (response == null) {
      return null;
    }
    return response.version().protocolName();
  }

  @Nullable
  @Override
  public String getProtocolVersion(
      HttpClientConfig request, @Nullable HttpClientResponse response) {
    if (response == null) {
      return null;
    }
    HttpVersion version = response.version();
    return version.majorVersion() + "." + version.minorVersion();
  }

  @Nullable
  @Override
  public String getPeerName(HttpClientConfig request) {
    return getHost(request);
  }

  @Nullable
  @Override
  public Integer getPeerPort(HttpClientConfig request) {
    return getPort(request);
  }

  @Nullable
  @Override
  protected InetSocketAddress getPeerSocketAddress(
      HttpClientConfig request, @Nullable HttpClientResponse response) {

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
  private static String getHost(HttpClientConfig request) {
    String baseUrl = request.baseUrl();
    String uri = request.uri();

    if (baseUrl != null && !isAbsolute(uri)) {
      return UrlParser.getHost(baseUrl);
    } else {
      return UrlParser.getHost(uri);
    }
  }

  @Nullable
  private static Integer getPort(HttpClientConfig request) {
    String baseUrl = request.baseUrl();
    String uri = request.uri();

    if (baseUrl != null && !isAbsolute(uri)) {
      return UrlParser.getPort(baseUrl);
    } else {
      return UrlParser.getPort(uri);
    }
  }

  private static boolean isAbsolute(String uri) {
    return uri != null && !uri.isEmpty() && !uri.startsWith("/");
  }
}
