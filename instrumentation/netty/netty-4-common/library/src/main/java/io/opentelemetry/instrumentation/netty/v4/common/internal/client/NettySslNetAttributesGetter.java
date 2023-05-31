/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.netty.v4.common.internal.client;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.netty.channel.socket.DatagramChannel;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class NettySslNetAttributesGetter
    implements NetClientAttributesGetter<NettySslRequest, Void> {

  @Override
  public String getTransport(NettySslRequest request, @Nullable Void unused) {
    return request.channel() instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }

  @Override
  public String getNetworkTransport(NettySslRequest request, @Nullable Void unused) {
    return request.channel() instanceof DatagramChannel ? "udp" : "tcp";
  }

  @Nullable
  @Override
  public String getPeerName(NettySslRequest nettySslRequest) {
    return null;
  }

  @Nullable
  @Override
  public Integer getPeerPort(NettySslRequest nettySslRequest) {
    return null;
  }

  @Nullable
  @Override
  public InetSocketAddress getPeerSocketAddress(NettySslRequest request, @Nullable Void unused) {
    if (request.remoteAddress() instanceof InetSocketAddress) {
      return (InetSocketAddress) request.remoteAddress();
    }
    return null;
  }
}
