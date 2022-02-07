/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.netty.common.client;

import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_TCP;
import static io.opentelemetry.semconv.trace.attributes.SemanticAttributes.NetTransportValues.IP_UDP;

import io.netty.channel.socket.DatagramChannel;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class NettySslNetAttributesGetter
    extends InetSocketAddressNetClientAttributesGetter<NettySslRequest, Void> {

  @Nullable
  @Override
  public InetSocketAddress getAddress(NettySslRequest request, @Nullable Void unused) {
    if (request.remoteAddress() instanceof InetSocketAddress) {
      return (InetSocketAddress) request.remoteAddress();
    }
    return null;
  }

  @Override
  public String transport(NettySslRequest request, @Nullable Void unused) {
    return request.channel() instanceof DatagramChannel ? IP_UDP : IP_TCP;
  }
}
