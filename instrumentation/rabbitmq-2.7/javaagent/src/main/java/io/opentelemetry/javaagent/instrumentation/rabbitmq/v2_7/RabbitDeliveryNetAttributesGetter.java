/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq.v2_7;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import javax.annotation.Nullable;

class RabbitDeliveryNetAttributesGetter
    implements NetworkAttributesGetter<DeliveryRequest, Void>,
        ServerAttributesGetter<DeliveryRequest> {

  @Override
  public String getServerAddress(DeliveryRequest request) {
    return request.getConnection().getAddress().getHostAddress();
  }

  @Override
  public Integer getServerPort(DeliveryRequest request) {
    return request.getConnection().getPort();
  }

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

  @Override
  public Integer getNetworkPeerPort(DeliveryRequest request, @Nullable Void response) {
    return request.getConnection().getPort();
  }
}
