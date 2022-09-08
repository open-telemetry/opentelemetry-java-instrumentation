/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import javax.annotation.Nullable;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClientConfig;
import reactor.netty.http.client.HttpClientResponse;

final class ReactorNettyNetClientAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<HttpClientConfig, HttpClientResponse> {

  @Nullable
  @Override
  public String transport(HttpClientConfig request, @Nullable HttpClientResponse response) {
    return null;
  }

  @Nullable
  @Override
  public String peerName(HttpClientConfig request, @Nullable HttpClientResponse response) {
    URI parsedUri = parseUri(request);
    return parsedUri == null ? null : parsedUri.getHost();
  }

  @Nullable
  @Override
  public Integer peerPort(HttpClientConfig request, @Nullable HttpClientResponse response) {
    URI parsedUri = parseUri(request);
    return parsedUri == null ? null : parsedUri.getPort();
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

  private static URI parseUri(HttpClientConfig request) {
    String baseUrl = request.baseUrl();
    String uri = request.uri();

    URI parsedUri;
    try {
      if (baseUrl != null && uri.startsWith("/")) {
        parsedUri = new URI(baseUrl);
      } else {
        parsedUri = new URI(uri);
      }
    } catch (URISyntaxException ignored) {
      return null;
    }
    return parsedUri;
  }
}
