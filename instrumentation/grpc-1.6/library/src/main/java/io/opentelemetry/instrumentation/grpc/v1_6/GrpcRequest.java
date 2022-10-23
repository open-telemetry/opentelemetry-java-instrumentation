/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import java.net.SocketAddress;
import java.net.URI;
import javax.annotation.Nullable;

public final class GrpcRequest {

  private final MethodDescriptor<?, ?> method;

  @Nullable private volatile Metadata metadata;

  @Nullable private volatile String logicalHost;
  private volatile int logicalPort = -1;
  @Nullable private volatile SocketAddress peerSocketAddress;

  GrpcRequest(
      MethodDescriptor<?, ?> method,
      @Nullable Metadata metadata,
      @Nullable SocketAddress peerSocketAddress,
      @Nullable String authority) {
    this.method = method;
    this.metadata = metadata;
    this.peerSocketAddress = peerSocketAddress;
    setLogicalAddress(authority);
  }

  private void setLogicalAddress(@Nullable String authority) {
    if (authority == null) {
      return;
    }
    try {
      URI uri = new URI(null, authority, null, null, null);
      logicalHost = uri.getHost();
      logicalPort = uri.getPort();
    } catch (Throwable e) {
      // do nothing
    }
  }

  public MethodDescriptor<?, ?> getMethod() {
    return method;
  }

  @Nullable
  public Metadata getMetadata() {
    return metadata;
  }

  void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  @Nullable
  public String getLogicalHost() {
    return logicalHost;
  }

  public int getLogicalPort() {
    return logicalPort;
  }

  @Nullable
  public SocketAddress getPeerSocketAddress() {
    return peerSocketAddress;
  }

  void setPeerSocketAddress(SocketAddress peerSocketAddress) {
    this.peerSocketAddress = peerSocketAddress;
  }
}
