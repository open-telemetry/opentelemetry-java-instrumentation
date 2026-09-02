/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.rabbit.v1_0;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import javax.annotation.Nullable;

class SpringRabbitNetAttributesGetter
    implements NetworkAttributesGetter<SpringRabbitRequest, Void>,
        ServerAttributesGetter<SpringRabbitRequest> {

  @Override
  public String getServerAddress(SpringRabbitRequest request) {
    return request.getChannel().getConnection().getAddress().getHostAddress();
  }

  @Override
  public Integer getServerPort(SpringRabbitRequest request) {
    return request.getChannel().getConnection().getPort();
  }

  @Nullable
  @Override
  public String getNetworkType(SpringRabbitRequest request, @Nullable Void unused) {
    InetAddress address = request.getChannel().getConnection().getAddress();
    if (address instanceof Inet4Address) {
      return "ipv4";
    } else if (address instanceof Inet6Address) {
      return "ipv6";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkPeerAddress(SpringRabbitRequest request, @Nullable Void unused) {
    return request.getChannel().getConnection().getAddress().getHostAddress();
  }

  @Override
  public Integer getNetworkPeerPort(SpringRabbitRequest request, @Nullable Void unused) {
    return request.getChannel().getConnection().getPort();
  }
}
