/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetServerAttributesGetter;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcRequest;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class GrpcNetServerAttributesGetter
    extends InetSocketAddressNetServerAttributesGetter<GrpcRequest> {

  @Nullable
  @Override
  public String getHostName(GrpcRequest grpcRequest) {
    return grpcRequest.getLogicalHost();
  }

  @Override
  public Integer getHostPort(GrpcRequest grpcRequest) {
    return grpcRequest.getLogicalPort();
  }

  @Override
  @Nullable
  protected InetSocketAddress getPeerSocketAddress(GrpcRequest request) {
    SocketAddress address = request.getPeerSocketAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }

  @Nullable
  @Override
  protected InetSocketAddress getHostSocketAddress(GrpcRequest grpcRequest) {
    // TODO: later version introduces TRANSPORT_ATTR_LOCAL_ADDR, might be a good idea to use it
    return null;
  }
}
