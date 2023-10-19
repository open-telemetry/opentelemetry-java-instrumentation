/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.rabbitmq.client.GetResponse;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation") // have to use the deprecated Net*AttributesGetter for now
public class RabbitReceiveNetAttributesGetter
    implements io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter<
        ReceiveRequest, GetResponse> {

  @Nullable
  @Override
  public String getSockFamily(ReceiveRequest request, @Nullable GetResponse response) {
    if (request.getConnection().getAddress() instanceof Inet6Address) {
      return "inet6";
    }
    return null;
  }

  @Nullable
  @Override
  public String getNetworkType(ReceiveRequest request, @Nullable GetResponse response) {
    InetAddress address = request.getConnection().getAddress();
    if (address instanceof Inet4Address) {
      return "ipv6";
    } else if (address instanceof Inet6Address) {
      return "ipv6";
    }
    return null;
  }

  @Nullable
  @Override
  public String getServerAddress(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Integer getServerPort(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getNetworkPeerAddress(ReceiveRequest request, @Nullable GetResponse response) {
    return request.getConnection().getAddress().getHostAddress();
  }

  @Nullable
  @Override
  public Integer getNetworkPeerPort(ReceiveRequest request, @Nullable GetResponse response) {
    return request.getConnection().getPort();
  }
}
