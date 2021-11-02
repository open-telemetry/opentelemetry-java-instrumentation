/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public final class GrpcRequest {

  private final MethodDescriptor<?, ?> method;
  @Nullable private final Metadata metadata;

  @Nullable private volatile SocketAddress remoteAddress;

  GrpcRequest(
      MethodDescriptor<?, ?> method,
      @Nullable Metadata metadata,
      @Nullable SocketAddress remoteAddress) {
    this.method = method;
    this.metadata = metadata;
    this.remoteAddress = remoteAddress;
  }

  public MethodDescriptor<?, ?> getMethod() {
    return method;
  }

  @Nullable
  public Metadata getMetadata() {
    return metadata;
  }

  @Nullable
  public SocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  void setRemoteAddress(SocketAddress remoteAddress) {
    this.remoteAddress = remoteAddress;
  }
}
