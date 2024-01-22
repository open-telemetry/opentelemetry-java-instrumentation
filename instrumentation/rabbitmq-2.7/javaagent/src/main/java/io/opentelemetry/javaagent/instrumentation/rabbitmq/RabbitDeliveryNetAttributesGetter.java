/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import javax.annotation.Nullable;

public class RabbitDeliveryNetAttributesGetter
    implements NetworkAttributesGetter<DeliveryRequest, Void> {

  @Nullable
  @Override
  public String getNetworkType(DeliveryRequest request, @Nullable Void response) {
    InetAddress address = request.getConnection().getAddress();
    if (address instanceof Inet4Address) {
      return "ipv4";
    } else if (address instanceof Inet6Address) {
      return "ipv6";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkPeerAddress(DeliveryRequest request, @Nullable Void response) {
    return request.getConnection().getAddress().getHostAddress();
  }

  @Nullable
  @Override
  public Integer getNetworkPeerPort(DeliveryRequest request, @Nullable Void response) {
    return request.getConnection().getPort();
  }
}
