/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofa.rpc;

import com.alipay.sofa.rpc.context.RpcInternalContext;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

final class SofaRpcNetworkServerAttributesGetter
    implements NetworkAttributesGetter<SofaRpcRequest, SofaResponse> {

  @Nullable
  @Override
  public InetSocketAddress getNetworkLocalInetSocketAddress(
      SofaRpcRequest request, @Nullable SofaResponse response) {
    // Try to get from request first (saved during SofaRpcRequest.create())
    InetSocketAddress localAddress = request.localAddress();
    if (localAddress != null) {
      return localAddress;
    }
    // Fallback to RpcInternalContext (address may be set after request creation)
    RpcInternalContext context = RpcInternalContext.getContext();
    if (context != null) {
      return context.getLocalAddress();
    }
    return null;
  }

  @Override
  @Nullable
  public String getNetworkPeerAddress(
      SofaRpcRequest request, @Nullable SofaResponse response) {
    // For SERVER span, network.peer represents the client address.
    // The remoteAddress is set in BoltServerProcessor.handleRequest() before filters execute,
    // and saved in SofaRpcRequest.create(), so we can get it from request.
    InetSocketAddress remoteAddress = request.remoteAddress();
    if (remoteAddress != null) {
      // Use getHostString() to get the hostname/IP without resolving
      return remoteAddress.getHostString();
    }
    // Fallback to RpcInternalContext (should be available, but may be cleared in some cases)
    RpcInternalContext context = RpcInternalContext.getContext();
    if (context != null) {
      InetSocketAddress contextRemoteAddress = context.getRemoteAddress();
      if (contextRemoteAddress != null) {
        return contextRemoteAddress.getHostString();
      }
    }
    return null;
  }

  @Override
  @Nullable
  public Integer getNetworkPeerPort(SofaRpcRequest request, @Nullable SofaResponse response) {
    // For SERVER span, network.peer represents the client address.
    // The remoteAddress is set in BoltServerProcessor.handleRequest() before filters execute,
    // and saved in SofaRpcRequest.create(), so we can get it from request.
    InetSocketAddress remoteAddress = request.remoteAddress();
    if (remoteAddress != null) {
      return remoteAddress.getPort();
    }
    // Fallback to RpcInternalContext (should be available, but may be cleared in some cases)
    RpcInternalContext context = RpcInternalContext.getContext();
    if (context != null) {
      InetSocketAddress contextRemoteAddress = context.getRemoteAddress();
      if (contextRemoteAddress != null) {
        return contextRemoteAddress.getPort();
      }
    }
    return null;
  }

  @Override
  @Nullable
  public InetSocketAddress getNetworkPeerInetSocketAddress(
      SofaRpcRequest request, @Nullable SofaResponse response) {
    // For SERVER span, network.peer represents the client address.
    // The remoteAddress is set in BoltServerProcessor.handleRequest() before filters execute,
    // and saved in SofaRpcRequest.create(), so we can get it from request.
    InetSocketAddress remoteAddress = request.remoteAddress();
    if (remoteAddress != null) {
      return remoteAddress;
    }
    // Fallback to RpcInternalContext (should be available, but may be cleared in some cases)
    RpcInternalContext context = RpcInternalContext.getContext();
    if (context != null) {
      return context.getRemoteAddress();
    }
    return null;
  }
}
