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
  public String getPeerName(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Integer getPeerPort(ReceiveRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getSockPeerAddr(ReceiveRequest request, @Nullable GetResponse response) {
    return request.getConnection().getAddress().getHostAddress();
  }

  @Nullable
  @Override
  public Integer getSockPeerPort(ReceiveRequest request, @Nullable GetResponse response) {
    return request.getConnection().getPort();
  }

  @Nullable
  @Override
  public String getSockFamily(ReceiveRequest request, @Nullable GetResponse response) {
    if (request.getConnection().getAddress() instanceof Inet6Address) {
      return "inet6";
    }
    return null;
  }
}
