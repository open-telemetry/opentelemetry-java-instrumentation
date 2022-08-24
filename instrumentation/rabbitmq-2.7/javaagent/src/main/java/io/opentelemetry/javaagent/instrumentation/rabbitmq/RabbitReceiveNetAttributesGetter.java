/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import com.rabbitmq.client.GetResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import java.net.Inet6Address;
import javax.annotation.Nullable;

public class RabbitReceiveNetAttributesGetter
    implements NetClientAttributesGetter<ReceiveRequest, GetResponse> {

  @Nullable
  @Override
  public String transport(ReceiveRequest request, @Nullable GetResponse response) {
    return null;
  }

  @Nullable
  @Override
  public String peerName(ReceiveRequest request, @Nullable GetResponse response) {
    return null;
  }

  @Nullable
  @Override
  public Integer peerPort(ReceiveRequest request, @Nullable GetResponse response) {
    return null;
  }

  @Nullable
  @Override
  public String sockPeerAddr(ReceiveRequest request, @Nullable GetResponse response) {
    return request.getConnection().getAddress().getHostAddress();
  }

  @Nullable
  @Override
  public String sockPeerName(ReceiveRequest request, @Nullable GetResponse response) {
    return null;
  }

  @Nullable
  @Override
  public Integer sockPeerPort(ReceiveRequest request, @Nullable GetResponse response) {
    return request.getConnection().getPort();
  }

  @Nullable
  @Override
  public String sockFamily(ReceiveRequest request, @Nullable GetResponse response) {
    if (request.getConnection().getAddress() instanceof Inet6Address) {
      return "inet6";
    }
    return null;
  }
}
