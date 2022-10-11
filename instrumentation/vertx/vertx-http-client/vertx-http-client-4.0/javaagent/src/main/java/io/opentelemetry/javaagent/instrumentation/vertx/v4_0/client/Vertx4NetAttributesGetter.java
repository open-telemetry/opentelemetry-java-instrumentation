/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v4_0.client;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.net.SocketAddress;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class Vertx4NetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<HttpClientRequest, HttpClientResponse> {

  @Override
  public String transport(HttpClientRequest request, @Nullable HttpClientResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String peerName(HttpClientRequest request) {
    return request.getHost();
  }

  @Override
  public Integer peerPort(HttpClientRequest request) {
    return request.getPort();
  }

  @Nullable
  @Override
  protected InetSocketAddress getPeerSocketAddress(
      HttpClientRequest request, @Nullable HttpClientResponse response) {
    if (response == null) {
      return null;
    }
    SocketAddress address = response.netSocket().remoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
