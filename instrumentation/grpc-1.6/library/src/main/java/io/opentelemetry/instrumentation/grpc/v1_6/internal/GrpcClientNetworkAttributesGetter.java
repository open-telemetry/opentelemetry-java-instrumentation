/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import io.grpc.Status;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcRequest;
import java.net.SocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class GrpcClientNetworkAttributesGetter
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

  @Override
  @Nullable
  public SocketAddress getNetworkLocalSocketAddress(
      GrpcRequest request, @Nullable Status response) {
    return request.getLocalSocketAddress();
  }

  @Override
  @Nullable
  public SocketAddress getNetworkPeerSocketAddress(GrpcRequest request, @Nullable Status response) {
    return request.getPeerSocketAddress();
  }
}
