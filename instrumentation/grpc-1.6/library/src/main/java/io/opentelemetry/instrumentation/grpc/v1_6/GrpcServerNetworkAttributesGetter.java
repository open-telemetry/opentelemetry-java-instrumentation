/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Status;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import java.net.SocketAddress;
import javax.annotation.Nullable;

final class GrpcServerNetworkAttributesGetter
    implements ServerAttributesGetter<GrpcRequest>, NetworkAttributesGetter<GrpcRequest, Status> {

  @Nullable
  @Override
  public String getServerAddress(GrpcRequest grpcRequest) {
    return grpcRequest.getServerAddress();
  }

  @Override
  @Nullable
  public Integer getServerPort(GrpcRequest grpcRequest) {
    return grpcRequest.getServerPort();
  }

  @Nullable
  @Override
  public SocketAddress getNetworkLocalSocketAddress(
      GrpcRequest grpcRequest, @Nullable Status status) {
    return grpcRequest.getLocalSocketAddress();
  }

  @Override
  @Nullable
  public SocketAddress getNetworkPeerSocketAddress(GrpcRequest request, @Nullable Status status) {
    return request.getPeerSocketAddress();
  }
}
