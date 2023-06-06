/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import io.grpc.Status;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcRequest;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class GrpcNetClientAttributesGetter
    implements NetClientAttributesGetter<GrpcRequest, Status> {

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
  public InetSocketAddress getServerInetSocketAddress(
      GrpcRequest request, @Nullable Status response) {
    SocketAddress address = request.getPeerSocketAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }
}
