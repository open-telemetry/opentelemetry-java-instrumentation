/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import io.grpc.Status;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcRequest;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class GrpcNetServerAttributesGetter
    implements NetServerAttributesGetter<GrpcRequest, Status> {

  @Nullable
  @Override
  public String getServerAddress(GrpcRequest grpcRequest) {
    return grpcRequest.getLogicalHost();
  }

  @Override
  public Integer getServerPort(GrpcRequest grpcRequest) {
    return grpcRequest.getLogicalPort();
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

  @Nullable
  @Override
  public InetSocketAddress getServerInetSocketAddress(
      GrpcRequest grpcRequest, @Nullable Status status) {
    // TODO: later version introduces TRANSPORT_ATTR_LOCAL_ADDR, might be a good idea to use it
    return null;
  }
}
