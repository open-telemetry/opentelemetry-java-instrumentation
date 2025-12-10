/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sofa.rpc.internal;

import com.alipay.sofa.rpc.client.ProviderInfo;
import com.alipay.sofa.rpc.context.RpcInternalContext;
import com.alipay.sofa.rpc.core.response.SofaResponse;
import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.sofa.rpc.SofaRpcRequest;
import java.net.InetSocketAddress;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SofaRpcClientNetworkAttributesGetter
    implements ServerAttributesGetter<SofaRpcRequest>,
        NetworkAttributesGetter<SofaRpcRequest, SofaResponse> {

  @Nullable
  @Override
  public String getServerAddress(SofaRpcRequest request) {
    ProviderInfo providerInfo = getProviderInfo();
    if (providerInfo != null) {
      return providerInfo.getHost();
    }

    // Fallback to remote address
    InetSocketAddress remoteAddress = request.remoteAddress();
    if (remoteAddress != null) {
      return remoteAddress.getHostString();
    }

    return null;
  }

  @Override
  @Nullable
  public Integer getServerPort(SofaRpcRequest request) {
    ProviderInfo providerInfo = getProviderInfo();
    if (providerInfo != null) {
      return providerInfo.getPort();
    }

    // Fallback to remote address
    InetSocketAddress remoteAddress = request.remoteAddress();
    if (remoteAddress != null) {
      return remoteAddress.getPort();
    }

    return null;
  }

  @Nullable
  private static ProviderInfo getProviderInfo() {
    RpcInternalContext context = RpcInternalContext.getContext();
    if (context != null) {
      return context.getProviderInfo();
    }
    return null;
  }

  @Override
  @Nullable
  public String getNetworkPeerAddress(
      SofaRpcRequest request, @Nullable SofaResponse response) {
    // For CLIENT span, network.peer represents the server address.
    // ProviderInfo is set in filterChain() before filters execute, so we can get it here.
    ProviderInfo providerInfo = getProviderInfo();
    if (providerInfo != null) {
      return providerInfo.getHost();
    }

    // Fallback to request.remoteAddress() (may be set in SofaRpcRequest.create())
    InetSocketAddress remoteAddress = request.remoteAddress();
    if (remoteAddress != null) {
      return remoteAddress.getHostString();
    }

    // Last fallback to RpcInternalContext (may be set after request creation in doSendMsg)
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
    // For CLIENT span, network.peer represents the server address.
    // ProviderInfo is set in filterChain() before filters execute, so we can get it here.
    ProviderInfo providerInfo = getProviderInfo();
    if (providerInfo != null) {
      return providerInfo.getPort();
    }

    // Fallback to request.remoteAddress() (may be set in SofaRpcRequest.create())
    InetSocketAddress remoteAddress = request.remoteAddress();
    if (remoteAddress != null) {
      return remoteAddress.getPort();
    }

    // Last fallback to RpcInternalContext (may be set after request creation in doSendMsg)
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
    // Try to get from request first (may be set in SofaRpcRequest.create())
    InetSocketAddress remoteAddress = request.remoteAddress();
    if (remoteAddress != null) {
      return remoteAddress;
    }

    // Fallback to RpcInternalContext (may be set after request creation in doSendMsg)
    RpcInternalContext context = RpcInternalContext.getContext();
    if (context != null) {
      return context.getRemoteAddress();
    }
    return null;
  }
}
