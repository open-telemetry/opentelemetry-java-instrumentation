/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.client;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.SocketAddress;
import javax.annotation.Nullable;

final class Vertx4NetAttributesGetter
    implements NetClientAttributesGetter<HttpClientRequest, HttpClientResponse> {

  @Override
  public String getProtocolName(HttpClientRequest request, @Nullable HttpClientResponse response) {
    return "http";
  }

  @Nullable
  @Override
  public String getProtocolVersion(
      HttpClientRequest request, @Nullable HttpClientResponse response) {
    HttpVersion version = request.version();
    if (version == null) {
      return null;
    }
    switch (version) {
      case HTTP_1_0:
        return "1.0";
      case HTTP_1_1:
        return "1.1";
      case HTTP_2:
        return "2.0";
    }
    return null;
  }

  @Nullable
  @Override
  public String getPeerName(HttpClientRequest request) {
    return request.getHost();
  }

  @Override
  public Integer getPeerPort(HttpClientRequest request) {
    return request.getPort();
  }

  @Nullable
  @Override
  public String getSockPeerAddr(HttpClientRequest request, @Nullable HttpClientResponse response) {
    if (response == null) {
      return null;
    }
    SocketAddress socketAddress = response.netSocket().remoteAddress();
    return socketAddress == null ? null : socketAddress.hostAddress();
  }

  @Nullable
  @Override
  public String getSockPeerName(HttpClientRequest request, @Nullable HttpClientResponse response) {
    if (response == null) {
      return null;
    }
    SocketAddress socketAddress = response.netSocket().remoteAddress();
    return socketAddress == null ? null : socketAddress.host();
  }

  @Nullable
  @Override
  public Integer getSockPeerPort(HttpClientRequest request, @Nullable HttpClientResponse response) {
    if (response == null) {
      return null;
    }
    SocketAddress socketAddress = response.netSocket().remoteAddress();
    return socketAddress == null ? null : socketAddress.port();
  }
}
