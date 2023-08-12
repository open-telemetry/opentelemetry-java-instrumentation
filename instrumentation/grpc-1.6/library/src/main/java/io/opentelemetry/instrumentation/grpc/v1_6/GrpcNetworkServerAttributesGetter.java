/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Status;
import io.opentelemetry.instrumentation.api.instrumenter.network.ClientAttributesGetter;
import io.opentelemetry.instrumentation.api.instrumenter.network.ServerAttributesGetter;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class GrpcNetworkServerAttributesGetter
    implements ServerAttributesGetter<GrpcRequest, Status>,
        ClientAttributesGetter<GrpcRequest, Status> {

  @Nullable
  @Override
  public String getServerAddress(GrpcRequest grpcRequest) {
    return grpcRequest.getLogicalHost();
  }

  @Override
  public Integer getServerPort(GrpcRequest grpcRequest) {
    return grpcRequest.getLogicalPort();
  }

  @Nullable
  @Override
  public InetSocketAddress getServerInetSocketAddress(
      GrpcRequest grpcRequest, @Nullable Status status) {
    // TODO: later version introduces TRANSPORT_ATTR_LOCAL_ADDR, might be a good idea to use it
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getClientInetSocketAddress(
      GrpcRequest request, @Nullable Status status) {
    SocketAddress address = request.getPeerSocketAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
