/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.grpc.v1_6;

import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcTargetParser;
import io.opentelemetry.instrumentation.grpc.v1_6.internal.ParsedTarget;
import java.net.SocketAddress;
import javax.annotation.Nullable;

public final class GrpcRequest {

  private final MethodDescriptor<?, ?> method;

  @Nullable private volatile Metadata metadata;
  @Nullable private final String serverAddress;
  @Nullable private final Integer serverPort;
  @Nullable private volatile SocketAddress peerSocketAddress;

  @Nullable private volatile Long requestSize;
  @Nullable private volatile Long responseSize;

  /**
   * Creates a client-side gRPC request.
   *
   * @param method the gRPC method descriptor
   * @param authority the channel authority (host:port)
   * @param parsedTarget the pre-parsed gRPC target (from {@link
   *     io.opentelemetry.instrumentation.grpc.v1_6.internal.GrpcTargetParser#parse}), or {@code
   *     null} if unavailable
   */
  static GrpcRequest createClientRequest(
      MethodDescriptor<?, ?> method,
      @Nullable String authority,
      @Nullable ParsedTarget parsedTarget) {
    ParsedTarget effective =
        parsedTarget != null ? parsedTarget : GrpcTargetParser.parseAuthority(authority);
    if (effective == null) {
      return new GrpcRequest(method, null, null, null, null);
    }
    return new GrpcRequest(method, null, null, effective.getAddress(), effective.getPort());
  }

  /**
   * Creates a server-side gRPC request.
   *
   * @param method the gRPC method descriptor
   * @param metadata the request metadata
   * @param peerSocketAddress the peer socket address
   * @param authority the request authority
   */
  static GrpcRequest createServerRequest(
      MethodDescriptor<?, ?> method,
      @Nullable Metadata metadata,
      @Nullable SocketAddress peerSocketAddress,
      @Nullable String authority) {
    ParsedTarget parsed = GrpcTargetParser.parseAuthority(authority);
    return new GrpcRequest(
        method,
        metadata,
        peerSocketAddress,
        parsed != null ? parsed.getAddress() : null,
        parsed != null ? parsed.getPort() : null);
  }

  private GrpcRequest(
      MethodDescriptor<?, ?> method,
      @Nullable Metadata metadata,
      @Nullable SocketAddress peerSocketAddress,
      @Nullable String serverAddress,
      @Nullable Integer serverPort) {
    this.method = method;
    this.metadata = metadata;
    this.peerSocketAddress = peerSocketAddress;
    this.serverAddress = serverAddress;
    this.serverPort = serverPort;
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

  /**
   * Returns the server address.
   *
   * <p>When a target string is available (from gRPC channel configuration), the server address is
   * extracted per the <a href="https://grpc.github.io/grpc/core/md_doc_naming.html">gRPC Name
   * Resolution spec</a>. Otherwise, falls back to the authority (host portion).
   */
  @Nullable
  public String getServerAddress() {
    return serverAddress;
  }

  /**
   * Returns the server port.
   *
   * <p>When a target string is available (from gRPC channel configuration), the server port is
   * extracted per the <a href="https://grpc.github.io/grpc/core/md_doc_naming.html">gRPC Name
   * Resolution spec</a>. Otherwise, falls back to the authority (port portion).
   */
  @Nullable
  public Integer getServerPort() {
    return serverPort;
  }

  /**
   * @deprecated Use {@link #getServerAddress()} instead.
   */
  @Deprecated
  @Nullable
  public String getLogicalHost() {
    return serverAddress;
  }

  /**
   * @deprecated Use {@link #getServerPort()} instead.
   */
  @Deprecated
  public int getLogicalPort() {
    return serverPort != null ? serverPort : -1;
  }

  @Nullable
  public SocketAddress getPeerSocketAddress() {
    return peerSocketAddress;
  }

  void setPeerSocketAddress(@Nullable SocketAddress peerSocketAddress) {
    this.peerSocketAddress = peerSocketAddress;
  }

  @Nullable
  public Long getRequestSize() {
    return requestSize;
  }

  public void setRequestSize(@Nullable Long requestSize) {
    this.requestSize = requestSize;
  }

  @Nullable
  public Long getResponseSize() {
    return responseSize;
  }

  public void setResponseSize(@Nullable Long responseSize) {
    this.responseSize = responseSize;
  }
}
