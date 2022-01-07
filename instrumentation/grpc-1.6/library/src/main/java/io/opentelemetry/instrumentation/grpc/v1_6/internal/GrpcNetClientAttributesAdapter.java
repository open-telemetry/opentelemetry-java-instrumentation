/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6.internal;

import io.grpc.Status;
import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetClientAttributesAdapter;
import io.opentelemetry.instrumentation.grpc.v1_6.GrpcRequest;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public final class GrpcNetClientAttributesAdapter
    extends InetSocketAddressNetClientAttributesAdapter<GrpcRequest, Status> {
  @Override
  @Nullable
  public InetSocketAddress getAddress(GrpcRequest request, @Nullable Status response) {
    SocketAddress address = request.getRemoteAddress();
    if (address instanceof InetSocketAddress) {
      return (InetSocketAddress) address;
    }
    return null;
  }

  @Override
  public String transport(GrpcRequest request, @Nullable Status response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }
}
