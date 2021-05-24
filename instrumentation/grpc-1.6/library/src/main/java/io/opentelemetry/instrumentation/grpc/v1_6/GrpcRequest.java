/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.net.SocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;

final class GrpcRequest {

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

  MethodDescriptor<?, ?> getMethod() {
    return method;
  }

  Metadata getMetadata() {
    return metadata;
  }

  SocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  void setRemoteAddress(SocketAddress remoteAddress) {
    this.remoteAddress = remoteAddress;
  }
}
